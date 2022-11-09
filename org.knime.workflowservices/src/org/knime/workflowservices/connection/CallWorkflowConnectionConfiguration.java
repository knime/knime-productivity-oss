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
 *   Created on 13 Oct 2021 by carlwitt
 */
package org.knime.workflowservices.connection;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.RelativeTo;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.IServerConnection.FailingJobRetentionPolicy;
import org.knime.workflowservices.connection.IServerConnection.SuccessfulJobRetentionPolicy;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;

/**
 * This can be passed to {@link IServerConnection#createWorkflowBackend(CallWorkflowConnectionConfiguration)} to create
 * a {@link IWorkflowBackend}. It serves as base class for the configuration classes of the various Call Workflow nodes.
 *
 * @see IServerConnection#validate(CallWorkflowConnectionConfiguration)
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class CallWorkflowConnectionConfiguration {

    /**
     * Since this class has been tightly interwoven with {@link IServerConnection}, it is reused for deprecated and
     * newer Call Workflow nodes.
     */
    private enum Version {
            /**
             * Stores the callee workflow path as a string in {@link #m_workflowPath}. That path is managed manually via
             * {@link CallWorkflowConnectionConfiguration#setWorkflowPath(String)}.
             *
             * All call workflow nodes using this version have a file system connector port at index 0.
             */
            VERSION_1,
            /**
             * Manages the callee workflow path via the settings model
             * {@link CallWorkflowConnectionConfiguration#getWorkflowChooserModel()}.
             *
             * Call workflow nodes using this version may have a file system connector port, see
             * {@link CallWorkflowConnectionConfiguration#getConnectorPortIndex()}.
             */
            VERSION_2;
    }

    // ---- persisted fields -----

    /** @see #getBackoffPolicy() */
    private Optional<BackoffPolicy> m_backoffPolicy = Optional.empty();

    /** @see #isDiscardJobOnSuccessfulExecution() */
    private boolean m_discardJobOnSuccessfulExecution = true;

    /** @see #isSynchronousInvocation() */
    private boolean m_isSynchronous;

    /** @see #isDiscardJobOnSuccessfulExecution() */
    private boolean m_keepFailingJobs = true;

    /** @see #getLoadTimeout() */
    private Optional<Duration> m_loadTimeout = Optional.empty();
    /**
     * Stores the number of seconds to wait for creating a job with a backend.
     *
     * @see #getLoadTimeout()
     */

    /** @see #getFetchParametersTimeout() */
    private Optional<Duration> m_fetchWorkflowParametersTimeout = Optional.empty();
    /**
     * Stores the number of seconds to wait for retrieving the input and output workflow parameters of a callee.
     *
     * @see #getFetchParametersTimeout()
     */

    /**
     * Used in {@link Version#VERSION_1} to manage the callee workflow path.
     *
     * @see #getWorkflowPath()
     */
    private String m_workflowPath;

    /**
     * Used in {@link Version#VERSION_2} to manage the callee workflow path.
     *
     * @see #getWorkflowPath()
     */
    private final SettingsModelWorkflowChooser m_workflowChooserModel;

    /** @see #getReportFormat() */
    private final CallWorkflowReportConfiguration m_reportConfiguration = new CallWorkflowReportConfiguration();

    // ---- non-persisted members -----

    private final NodeModelStatusConsumer m_statusConsumer;

    private final PortsConfiguration m_portsConfiguration;

    /**
     * The name of the port group that may or may not contain the file system input port. Non-null if and only if
     * version equals {@link Version#VERSION_2}.
     */
    private final String m_fileSystemPortGroupName;

    private final Version m_version;

    // constructor

    /**
     * Only for legacy Call Workflow nodes (that used to manage workflow path via a text box) and for manually
     * establishing a connection, e.g., to fetch callee workflow parameters.
     *
     * New nodes using the file system browser use the
     * {@link #CallWorkflowConnectionConfiguration(NodeCreationConfiguration, String)} constructor, in which case the
     * callee workflow path is managed via a settings model instead of a plain string.
     */
    public CallWorkflowConnectionConfiguration() {
        m_workflowChooserModel = null;
        m_statusConsumer = null;
        m_portsConfiguration = null;
        m_fileSystemPortGroupName = null;
        m_version = Version.VERSION_1;
    }

    /**
     * @param creationConfig a node's ports might include a file system connector port or the like that provides access
     *            to the location of the callee
     * @param inputPortGroupName the identifier of the port group that contains the callee location input port
     */
    public CallWorkflowConnectionConfiguration(final NodeCreationConfiguration creationConfig,
        final String inputPortGroupName) {
        CheckUtils.checkNotNull(inputPortGroupName,
            "The identifier of the file system connector input port group must not be null.");

        var portConfig = creationConfig.getPortConfig();
        if (portConfig.isEmpty()) {
            throw new IllegalStateException("No port configuration passed to the Call Workflow configuration.");
        }

        m_portsConfiguration = portConfig.get();

        m_workflowChooserModel = new SettingsModelWorkflowChooser("calleeWorkflow", inputPortGroupName, portConfig.get());
        // if more than the mandatory input table is present, we have a connector

        m_statusConsumer = new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));
        m_fileSystemPortGroupName = inputPortGroupName;
        m_version = Version.VERSION_2;
    }

    // save & load

    /**
     * @param settings writes member variables to this object
     */
    protected void saveSettings(final NodeSettingsWO settings) {
        settings.addBoolean("discardJobOnSuccessfulExecution", m_discardJobOnSuccessfulExecution);
        settings.addBoolean("isSynchronous", m_isSynchronous);
        settings.addBoolean("keepFailingJobs", m_keepFailingJobs);

        if (m_version == Version.VERSION_1) {
            settings.addString("workflow", getWorkflowPath());
        } else {
            m_workflowChooserModel.saveSettingsTo(settings);
        }

        m_reportConfiguration.save(settings);
        m_loadTimeout.ifPresent(duration -> settings.addInt("loadTimeout", (int)duration.getSeconds()));
        m_fetchWorkflowParametersTimeout
            .ifPresent(duration -> settings.addInt("fetchParametersTimeout", (int)duration.getSeconds()));
        m_backoffPolicy.ifPresent(backoffPolicy -> backoffPolicy.saveToSettings(settings));
        m_reportConfiguration.save(settings);
    }

    /**
     * Populate the fields from the settings object.
     *
     * @param settings
     * @param strict if
     * @throws InvalidSettingsException
     */
    protected void loadBaseSettings(final NodeSettingsRO settings, final boolean strict)
        throws InvalidSettingsException {

        if (m_version == Version.VERSION_1) {
            m_workflowPath = strict ? //
                settings.getString("workflow") : //
                settings.getString("workflow", "");
        } else {
            m_workflowChooserModel.loadSettingsFrom(settings);
        }

        m_isSynchronous = strict ? //
            settings.getBoolean("isSynchronous") : //
            settings.getBoolean("isSynchronous", false);

        // non-strict for backwards compatibility
        m_discardJobOnSuccessfulExecution = settings.getBoolean("discardJobOnSuccessfulExecution", true);
        m_keepFailingJobs = settings.getBoolean("keepFailingJobs", true);

        // base, multiplier, and retries
        m_backoffPolicy = BackoffPolicy.loadFromSettings(settings);

        // timeouts: load workflow timeout and parameter fetch timeout
        try {
            m_loadTimeout = Optional.of(Duration.ofSeconds(Math.max(settings.getInt("loadTimeout"), 0)));
        } catch (InvalidSettingsException e) { // NOSONAR backward compatibility
            m_loadTimeout = Optional.empty();
        }
        try {
            m_fetchWorkflowParametersTimeout =
                Optional.of(Duration.ofSeconds(Math.max(settings.getInt("fetchParametersTimeout"), 0)));
        } catch (InvalidSettingsException e) { // NOSONAR backward compatibility
            m_fetchWorkflowParametersTimeout = Optional.empty();
        }

    }

    /**
     * Load settings. Fix missing and problematic values where possible, otherwise throw an exception.
     *
     * @param settings to load from
     * @throws NotConfigurableException see {@link NodeDialogPane#loadSettingsFrom(NodeSettingsRO, DataTableSpec[])}
     */
    protected void loadSettingsInDialog(final NodeSettingsRO settings) throws NotConfigurableException {
        try {
            loadBaseSettings(settings, false);
            m_reportConfiguration.loadInDialog(settings);
        } catch (InvalidSettingsException e) { //NOSONAR
            // doesn't happen when passing strict = false
        }
    }

    /**
     * Initializes members from the given settings. Missing and problematic values will lead to an exception.
     *
     * @param settings
     * @param connection to validate the settings
     * @throws InvalidSettingsException
     */
    protected void loadSettingsInModel(final NodeSettingsRO settings, final IServerConnection connection)
        throws InvalidSettingsException {
        loadBaseSettings(settings, true);
        m_reportConfiguration.loadInModel(settings);
        IServerConnection.validateConfiguration(this, connection);
    }

    // getters & setters

    /**
     * @return Absolute or relative path to the local or remote workflow to execute. For instance
     *         <ul>
     *         <li>/Components/Workflow</li>
     *         <li>../Callee Workflows/Some other workflow</li>
     *         </ul>
     */
    public String getWorkflowPath() {
        if (m_version == Version.VERSION_1) {
            return m_workflowPath;
        } else {
            return getWorkflowPathFromChooser();
        }
    }

    /**
     * Only used in {@link Version#VERSION_2}.
     *
     * @return a path that's compatible with what
     *         {@link IServerConnection#createWorkflowBackend(CallWorkflowConnectionConfiguration)} expects.
     *         Specifically, mountpoint-relative callee paths have to be absolute ("/Path/To/Callee") and
     *         workflow-relative paths have to start with a reference to the parent directory ("../Path/To/Relative")
     */
    private String getWorkflowPathFromChooser() {
        CheckUtils.checkState(m_version != Version.VERSION_1, "Coding error.");
        final String path = m_workflowChooserModel.getLocation().getPath();
        // by LocalWorkflowBackend convention, a workflow specified relative to a mountpoint must be specified as
        // absolute path, e.g., "/Callees/SomeWorkflow". However, selecting a mountpoint-relative workflow in a
        // workflow chooser won't give "/" as prefix, e.g., "Callees/SomeWorkflow"
        if(m_workflowChooserModel.getLocation().getFSCategory() == FSCategory.RELATIVE) {
            var relativeToSpecifier = m_workflowChooserModel.getLocation().getFileSystemSpecifier();
            if(Objects.equals(relativeToSpecifier.orElse(null), RelativeTo.MOUNTPOINT.getSettingsValue())) {
                return "/" + path;
            }
        }
        return path;
    }

    /**
     * @param workflowPath see {@link #getWorkflowPath()}
     * @return this
     * @throws IllegalStateException if not called on an instance constructed with the constructor
     *             {@link #CallWorkflowConnectionConfiguration()} which is for non-interactive and legacy
     *             configurations. If it was for interactive, then the path must only be set using the dialog component
     *             connected to the settings model for the callee workflow path.
     */
    public CallWorkflowConnectionConfiguration setWorkflowPath(final String workflowPath) {
        if (m_version != Version.VERSION_1) {
            throw new IllegalStateException();
        }
        m_workflowPath = workflowPath;
        return this;
    }

    /**
     * @return defines the number of retries, etc. when using job status polling, i.e.,
     *         {@link #isSynchronousInvocation()} returns false
     */
    public Optional<BackoffPolicy> getBackoffPolicy() {
        return m_backoffPolicy;
    }

    /**
     * @param policy during job status polling during asynchronous remote workflow execution
     * @return this for fluid API
     */
    public CallWorkflowConnectionConfiguration setBackoffPolicy(final BackoffPolicy policy) {
        m_backoffPolicy = Optional.of(policy);
        return this;
    }

    /**
     * @return what to do with a job with abnormal exit status
     */
    public FailingJobRetentionPolicy getFailingJobPolicy() {
        return isKeepFailingJobs() //
            ? FailingJobRetentionPolicy.KEEP_FAILING_JOBS //
            : FailingJobRetentionPolicy.DELETE_FAILING_JOBS;
    }

    /**
     * @return time after which to give up on creating a job to control remote workflow execution with. This happens in
     *         remote implementations of {@link IWorkflowBackend#loadWorkflow()}, such as
     *         RemoteWorkflowBackend#createJob().
     */
    public Optional<Duration> getLoadTimeout() {
        return m_loadTimeout;
    }

    /**
     * @param timeout {@link #getLoadTimeout()}
     * @return this
     */
    public CallWorkflowConnectionConfiguration setLoadTimeout(final Duration timeout) {
        m_loadTimeout = Optional.of(timeout);
        return this;
    }

    /**
     * @return time after which to give up waiting for the remote side to provide a description of the callee workflow
     *         input and output parameters when configuring the node via its dialog.
     */
    public Optional<Duration> getFetchParametersTimeout() {
        return m_fetchWorkflowParametersTimeout;
    }

    /**
     * @param timeout {@link #getFetchParametersTimeout()}
     * @return this
     */
    public CallWorkflowConnectionConfiguration setFetchParametersTimeout(final Duration timeout) {
        m_fetchWorkflowParametersTimeout = Optional.of(timeout);
        return this;
    }

    /**
     * @return what to do with successful remote jobs, e.g., keep or delete
     */
    public SuccessfulJobRetentionPolicy getSuccessfulJobPolicy() {
        return isDiscardJobOnSuccessfulExecution() //
            ? SuccessfulJobRetentionPolicy.DELETE_SUCCESSFUL_JOBS //
            : SuccessfulJobRetentionPolicy.KEEP_SUCCESSFUL_JOBS;
    }

    /**
     * Returns a flag determining if successful jobs should be kept.
     *
     * @return true if successful jobs should be kept
     */
    public boolean isDiscardJobOnSuccessfulExecution() {
        return m_discardJobOnSuccessfulExecution;
    }

    /**
     * Sets a flag determining if successful jobs should be kept.
     *
     * @param discardJobOnSuccessfulExecution flag determining if successful jobs should be kept
     * @return this
     */
    public CallWorkflowConnectionConfiguration
        setDiscardJobOnSuccessfulExecution(final boolean discardJobOnSuccessfulExecution) {
        m_discardJobOnSuccessfulExecution = discardJobOnSuccessfulExecution;
        return this;
    }

    /**
     * Sets a flag determining if failing jobs should be kept.
     *
     * @return true if failing jobs should be kept
     */
    public boolean isKeepFailingJobs() {
        return m_keepFailingJobs;
    }

    /**
     * Sets a flag determining if failing jobs should be kept.
     *
     * @param keepFailingJobs flag determining if failing jobs should be kept
     * @return this
     */
    public CallWorkflowConnectionConfiguration setKeepFailingJobs(final boolean keepFailingJobs) {
        m_keepFailingJobs = keepFailingJobs;
        return this;
    }

    /**
     * @return if true, no job status polling takes place ("short duration" option in
     *         {@link CallWorkflowConnectionControls})
     */
    public boolean isSynchronousInvocation() {
        return m_isSynchronous;
    }

    /**
     * @param isSynchronous if true, execute the remote workflow call synchronously, otherwise use job status polling
     * @return this for fluent API
     */
    public CallWorkflowConnectionConfiguration setSynchronousInvocation(final boolean isSynchronous) {
        m_isSynchronous = isSynchronous;
        return this;
    }

    /** @return the format of the report to be generated in the callee workflow */
    public Optional<RptOutputFormat> getReportFormat() {
        return m_reportConfiguration.getReportFormat();
    }

    /**
     * @param reportFormatOrNull the report format or null if no report should be generated
     * @return this for fluent API
     */
    public CallWorkflowConnectionConfiguration setReportFormat(final RptOutputFormat reportFormatOrNull) {
        m_reportConfiguration.setReportFormat(reportFormatOrNull);
        return this;
    }

    /**
     * @return the settings model for the callee workflow, i.e., the workflow to be executed by a Call Workflow node
     */
    public SettingsModelWorkflowChooser getWorkflowChooserModel() {
        return m_workflowChooserModel;
    }

    /**
     * @param specs
     */
    public void configureCalleeModel(final PortObjectSpec[] specs) throws InvalidSettingsException {
        m_workflowChooserModel.configureInModel(specs, m_statusConsumer);
    }

    /**
     * @return the connectorPresent
     */
    public boolean isConnectorPresent() {
        return getConnectorPortIndex().isPresent();
    }

    /**
     * @return the offset of the port that provides access to the callee workflow location. This port is ignored when
     *         preparing the input data for the callee.
     */
    public Optional<Integer> getConnectorPortIndex() {
        switch(m_version) {
        case VERSION_1:
            // deprecated nodes all had a connector port (always present but optional)
            return Optional.of(0);
        case VERSION_2:
            // new nodes can be configured to have a file system connector port if necessary
            return getConnectorPortIndex(m_fileSystemPortGroupName, m_portsConfiguration);
        default:
            throw new IllegalStateException("Coding error: Unhandled version " + m_version);
        }
    }

    /**
     * Used for {@link Version#VERSION_2} nodes to extract the offset of the file system connector input port, if any.
     *
     * @param fileSystemPortGroupName
     * @param portsConfiguration
     * @return
     */
    private static Optional<Integer> getConnectorPortIndex(final String fileSystemPortGroupName,
        final PortsConfiguration portsConfiguration) {
        if (fileSystemPortGroupName == null) {
            return Optional.empty();
        }
        int[] fileSystemPortLocation = portsConfiguration.getInputPortLocation().get(fileSystemPortGroupName);

        if (fileSystemPortLocation != null && fileSystemPortLocation.length > 0) {
            if (fileSystemPortLocation.length > 1) {
                throw new IllegalStateException("Coding error: More than one file system port connector present.");
            }
            return Optional.of(fileSystemPortLocation[0]);
        } else {
            return Optional.empty();
        }
    }

    /**
     * To be called in NodeModel validate methods.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    public void validateConfigurationForModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        // the deprecated nodes do not have a workflow chooser
        if (m_workflowChooserModel != null) {
            m_workflowChooserModel.validateSettings(settings);
        }
        if (!settings.containsKey("isSynchronous")) {
            throw new InvalidSettingsException(
                "Settings do not specify whether callee invocation should be synchronous.");
        }
    }

    /**
     * @return the ports configuration that might contain an input port that provides access to a callee workflow
     *         location
     */
    public Optional<PortsConfiguration> getPortsConfiguration() {
        return Optional.ofNullable(m_portsConfiguration);
    }

    /**
     * @return a connection suitable for creating a backend for retrieving the input and output workflow parameters of a
     *         callee workflow.
     */
    public CallWorkflowConnectionConfiguration createParameterFetchConfiguration() {
        final var result = new CallWorkflowConnectionConfiguration();
        result.setWorkflowPath(getWorkflowPath());
        // not sure if these are needed
        result.setKeepFailingJobs(false);
        result.setDiscardJobOnSuccessfulExecution(true);
        // the load timeout is considered when creating a workflow backend
        // the fetch parameters timeout is only persisted, not read by the backend
        result.setLoadTimeout(getFetchParametersTimeout().orElse(Duration.ZERO));
        return result;
    }

    /**
     * @return an error message if the configuration is not suitable to create an {@link IWorkflowBackend}
     */
    public Optional<String> validateForCreateWorkflowBackend() {
        if(m_version == Version.VERSION_1) {
            CallWorkflowUtil.PlainWorkflowPathFormat.validate(getWorkflowPath());
        }
        return Optional.empty();
    }

}