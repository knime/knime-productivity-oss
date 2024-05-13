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
 *   Created on 20 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.workflowservices.Fetcher.ConnectionCallable;
import org.knime.workflowservices.Fetcher.StatefulConsumer;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.knime.caller.WorkflowParameters;

/**
 * Controls fetching information about workflows as executables.
 *
 * @param <T> invocation target type, e.g., {@link FSLocation} for adhoc execution or String for deployment execution.
 * @param <P> workflow parameter type, e.g., {@link WorkflowParameters} for Call Workflow Service or
 *            {@code Map<String, ExternalNodeData>} for Call Workflow (Table/Row based)
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class CalleeParameterFlow<T, P> implements CalleePropertyFlow {

    final InvocationTargetProvider<T> m_invocationTarget;

    final CallWorkflowConnectionConfiguration m_configuration;

    final Fetcher.ConnectionCallable<P> m_fetchParameters;

    final Fetcher.StatefulConsumer<P> m_parametersControl;

    private Fetcher<P> m_parameterFetcher;

    /** Whether listeners are enabled. */
    protected volatile boolean m_enabled;

    // -----------------------------------------------------------------------------------------------------------------
    // External operations
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * @param configuration Used to fetch remote data but also to store the dialog state (selected version, workflow
     *            parameters).
     * @param invocationTarget callee workflow or deployment
     * @param parametersControl receives fetched workflow parameters
     * @param fetchParameters runnable to fetch workflow parameters from a connection
     */
    public CalleeParameterFlow(final CallWorkflowConnectionConfiguration configuration,
        final InvocationTargetProvider<T> invocationTarget, final StatefulConsumer<P> parametersControl,
        final ConnectionCallable<P> fetchParameters) {
        m_invocationTarget = invocationTarget;
        m_parametersControl = parametersControl;
        m_configuration = configuration;
        m_fetchParameters = fetchParameters;

        // when the location changes refetch versions and parameters
        invocationTarget.addChangeListener(e -> invocationTargetUpdated());
    }

    @Override
    public void enable(final boolean enabled) {
        m_enabled = enabled;
    }

    @Override
    public InvocationTargetProvider<T> getInvocationTarget() {
        return m_invocationTarget;
    }

    @Override
    public void loadInvocationTargets() {
        new LoadAndFetchWorker().run();
    }

    private final class LoadAndFetchWorker extends SwingWorkerWithContext<Void, Void> {
        @Override
        protected Void doInBackgroundWithContext() {
            m_invocationTarget.loadInvocationTargets(m_configuration);
            invocationTargetUpdated();
            return null;
        }
    }

    @Override
    public void invocationTargetUpdated() {
        m_invocationTarget.saveToConfiguration(m_configuration);

        // update gui
        m_parametersControl.clear();

        // during load, do not alter configuration data in response to location changes
        // during load, do not fetch data, as we're likely in inconsistent state
        // after load, do not fetch data for invalid locations
        // after load, refetch data for same location - refresh the dialog when re-opening
        if (m_enabled && m_invocationTarget.isLocationValid()) {
            // location is expected to be set on configuration when this method is called
            fetchParametersAsync();
        }
    }

    @Override
    public void close() {
        Optional.ofNullable(m_parameterFetcher).ifPresent(f -> f.cancel(true));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Asynchronous wrappers
    // -----------------------------------------------------------------------------------------------------------------

    void fetchParametersAsync() {
        var ready = m_parameterFetcher == null || m_parameterFetcher.isDone() || m_parameterFetcher.cancel(true);
        if (!ready) {
            m_parametersControl.exception("Failed to fetch workflow parameters.");
            return;
        }
        m_parameterFetcher =
            new Fetcher<>(m_configuration.createFetchConfiguration(), m_parametersControl, m_fetchParameters);
        m_parameterFetcher.execute();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------------------------------------------------

    private InvalidSettingsException connectionCannotBeCreated() {
        return new InvalidSettingsException(String.format(
            "Can not create the workflow execution connection for the workflow path '%s'", m_invocationTarget.get()));
    }

    WorkflowExecutionConnector createConnection(final CallWorkflowConnectionConfiguration configuration)
        throws InvalidSettingsException {
        return ConnectionUtil.createConnection(configuration).orElseThrow(this::connectionCannotBeCreated);
    }

}