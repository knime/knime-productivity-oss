/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on 7 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.util.hub.ItemVersion;
import org.knime.core.util.hub.NamedItemVersion;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.workflowservices.Fetcher.ConnectionCallable;
import org.knime.workflowservices.Fetcher.Processor;
import org.knime.workflowservices.Fetcher.StatefulConsumer;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.knime.caller.WorkflowParameters;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * This class manages the asynchronous aspects of interacting with the callee selection user interface, i.e., fetching
 * data from a server and keeping the user interface in sync.
 *
 * <h3>Data fetching</h3>
 * <ul>
 * <li>The flow can be disabled during loading to avoid reactin to change events while in inconsistent state.</li>
 * <li>When a callee workflow is selected, the available versions are fetched.</li>
 * <li>When a version is selected, the workflow parameters are fetched.</li>
 * </ul>
 *
 * <h3>GUI synchronization</h3>
 * <ol>
 * <li>The workflow selector publishes callee workflow changes.</li>
 * <li>The version selector subscribes to callee changes. It disables if there are no versions and enables if otherwise.
 * It publishes a modified callee upon selecting or restoring a version.</li>
 * <li>The parameter selector consumes the current callee and disables if no parameters are available.</li>
 * </ol>
 *
 * @param <T> invocation target type, e.g., {@link FSLocation} for adhoc execution or String for deployment execution.
 * @param <P> workflow parameter type, e.g., {@link WorkflowParameters} for Call Workflow Service or
 *            {@code Map<String, ExternalNodeData>} for Call Workflow (Table/Row based)
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @noreference not public API
 */
public final class HubCalleeSelectionFlow<T, P> extends CalleeParameterFlow<T, P> {

    /**
     * Data needed for dialog interactions. The data that is persisted (location, selected version, etc.) is stored in
     * the {@link CallWorkflowConnectionConfiguration}. Auxiliary data is held in the user interface, e.g., available
     * versions are held in {@link CalleeVersionSelectionPanel}.
     */
    static class DialogData<T> {
        /**
         * The {@link CallWorkflowConnectionConfiguration#getWorkflowChooserModel()} also claims a valid location for
         * spaces, workflow groups etc.
         */
        boolean m_isLocationValid;

        /**
         * When changing location, the selected version is reset. Otherwise, e.g., re-fetching versions after loading,
         * it is not.
         */
        T m_previousCalleeLocation;
    }

    final DialogData<T> m_data = new DialogData<>();

    final Fetcher.Processor<List<NamedItemVersion>, ItemVersion> m_versionsControl;

    Fetcher<List<NamedItemVersion>> m_versionFetcher;


    // -----------------------------------------------------------------------------------------------------------------
    // External operations
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * @param configuration Used to fetch remote data but also to store the dialog state (selected version, workflow
     *            parameters).
     * @param invocationTarget callee workflow or deployment
     * @param versionsControl sends version change events (user interaction or flow variable) and receives fetched
     *            available versions
     * @param parametersControl receives fetched workflow parameters
     * @param fetchParameters
     */
    public HubCalleeSelectionFlow(final CallWorkflowConnectionConfiguration configuration,
        final InvocationTargetProvider<T> invocationTarget,
        final Processor<List<NamedItemVersion>, ItemVersion> versionsControl,
        final StatefulConsumer<P> parametersControl, final ConnectionCallable<P> fetchParameters) {
        super(configuration, invocationTarget, parametersControl, fetchParameters);
        m_versionsControl = versionsControl;

        // when the version changes, refetch workflow parameters
        versionsControl.addListener(e -> versionChanged((ItemVersion)e.getNewValue()));
    }

    @Override
    public void invocationTargetUpdated() {
        ViewUtils.runOrInvokeLaterInEDT(this::invocationTargetUpdatedInternal);
    }

    private void invocationTargetUpdatedInternal() {
        final var newLocation = m_invocationTarget.get();
        final boolean sameLocation = Objects.equal(m_data.m_previousCalleeLocation, newLocation);

        // update dialog data
        m_data.m_previousCalleeLocation = newLocation;
        // Note that a space is also a valid location but does not have versions.
        // Note that a valid location may still cause a NoSuchFileException.
        m_data.m_isLocationValid = m_invocationTarget.isLocationValid();

        // update gui if we need to reinitialize for new location
        if (!sameLocation) {
            m_versionsControl.clear();
            m_parametersControl.clear();
        }

        // during load, do not alter configuration data in response to location changes
        // during load, do not fetch data, as we're likely in inconsistent state
        // after load, do not fetch data for invalid locations
        // after load, refetch data for same location - refresh the dialog when re-opening
        if (m_enabled && m_invocationTarget.isLocationValid()) {
            // location is expected to be set on configuration when this method is called

            // version is expected to be set after load
            // version control is expected to reflect node settings/user selection after load
            // if this is a new location, reset the version to current state
            if (!sameLocation) {
                // this would normally fire an event that leads to versionChanged but
                // the control is disabled because clear() was called earlier
                m_versionsControl.set(ItemVersion.currentState());
            }
            // take into account
            m_configuration.setItemVersion(m_versionsControl.get());

            // get remote data
            fetchVersionsAsync();
            fetchParametersAsync();
        }
    }

    /**
     * @param version the new version as reported by the version selector - either via user interaction or flow variable
     *            event
     */
    public void versionChanged(final ItemVersion version) {
        ViewUtils.runOrInvokeLaterInEDT(() -> {
            if (!m_enabled) {
                // do not fetch parameters
                return;
            }

            // version was determined to be invalid
            if (version == null) {
                m_parametersControl.clear();
                return;
            }

            // the source of this event is the version selector control, so no need to update its state

            // update configuration
            m_configuration.setItemVersion(version);

            if (m_data.m_isLocationValid) {
                fetchParametersAsync();
            } else {
                // this update was triggered by the bound flow variable or by loading the node settings
            }
        });
    }

    @Override
    public void close() {
        super.close();
        Optional.ofNullable(m_versionFetcher).ifPresent(f -> f.cancel(true));
    }



    // -----------------------------------------------------------------------------------------------------------------
    // Asynchronous wrappers
    // -----------------------------------------------------------------------------------------------------------------

    void fetchVersionsAsync() {
        var ready = m_versionFetcher == null || m_versionFetcher.isDone() || m_versionFetcher.cancel(true);
        if (!ready) {
            m_versionsControl.exception("Failed to fetch versions.");
            return;
        }
        m_versionFetcher =
            new Fetcher<>(m_configuration.createFetchConfiguration(), m_versionsControl, this::fetchVersions);
        m_versionFetcher.execute();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Synchronous fetch operations
    // -----------------------------------------------------------------------------------------------------------------

    private List<NamedItemVersion> fetchVersions(final CallWorkflowConnectionConfiguration configuration)
        throws InvalidSettingsException, IOException {
        final var connection = createConnection(configuration);
        final var versions = connection.getItemVersions();
        // sort by newest versions first
        return ImmutableList.sortedCopyOf(Comparator.comparing(NamedItemVersion::version).reversed(), versions);
    }

}
