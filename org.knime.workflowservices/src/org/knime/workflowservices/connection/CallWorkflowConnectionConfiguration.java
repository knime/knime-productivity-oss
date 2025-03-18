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

import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
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
import org.knime.core.util.hub.ItemVersion;
import org.knime.core.util.hub.ItemVersionStringPersistor;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;
import org.knime.workflowservices.ConnectorPortGroup;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.connection.util.ConnectionUtil.FailingJobRetentionPolicy;
import org.knime.workflowservices.connection.util.ConnectionUtil.SuccessfulJobRetentionPolicy;

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
     * Since this class has been tightly interwoven with {@link IServerConnection}, it was extended for newer Call
     * Workflow nodes which use file system connector ports.
     */
    private enum Version {
            /**
             * Stores the callee workflow path as a string in {@link #m_workflowPath}. That path is managed manually via
             * {@link CallWorkflowConnectionConfiguration#setWorkflowPath(String)}.
             *
             * All call workflow nodes using this version have a file system connector port at index 0.
             * It is also used for temporary connection configurations used to fetch workflow parameters.
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

    /** @see #getFetchParametersTimeout() */
    private Optional<Duration> m_fetchWorkflowParametersTimeout = Optional.empty();

    /**
     * Used in {@link Version#VERSION_1} to manage the callee workflow path.
     *
     * @see #getWorkflowPath()
     */
    private String m_workflowPath;

    /** @see #getReportFormat() */
    private final CallWorkflowReportConfiguration m_reportConfiguration = new CallWorkflowReportConfiguration();

    /** Internally used to switch between legacy and current code paths. */
    private final Version m_version;

    /** Hub repository item version of the workflow to execute. */
    private ItemVersion m_itemVersion = ItemVersion.currentState();

    // ---- version 2 exclusive members ----

    /**
     * Used in {@link Version#VERSION_2} to manage the callee workflow path.
     *
     * @see #getWorkflowPath()
     */
    private SettingsModelWorkflowChooser m_workflowChooserModel;

    /**
     * Used in {@link Version#VERSION_2} for ad hoc workflow invocation on the hub. Stores the id of the execution
     * context that provides the compute resources to execute the selected workflow. For instance
     * <code>11111111-1111-1111-1111-111111111111</code>.
     */
    private String m_executionContext;

    /**
     * Used in {@link Version#VERSION_2}. Stores the id of the deployed workflow to execute. For instance
     * <code>rest:50baa45b-ac5e-45b8-b443-3899f2ce87fe</code>.
     */
    private String m_deploymentId;

    /** Used in {@link Version#VERSION_2} to authenticate to a hub instance. */
    private AbstractHubAuthenticationPortObjectSpec m_hubAuthentication;

    /** Used in {@link Version#VERSION_2} to distinguish between calling deployments and ad hoc workflow invocation */
    private ConnectionType m_connectionType;

    /** Used in {@link Version#VERSION_2} for the workflow chooser (ad hoc workflow execution) */
    private final NodeModelStatusConsumer m_statusConsumer;

    /** Stores the port configuration of the node to inspect the possibly present file system/hub authenticator port.
     * The call workflow node's port configuration and the name of the port group that may or may not contain the file
     * system input port. Non-null if and only if version equals {@link Version#VERSION_2}.
     */
    private final ConnectorPortGroup m_connectorPortGroup;

    // constructors

    /**
     * Only for legacy Call Workflow nodes (that used to manage workflow path via a text box) and for manually
     * establishing a connection, e.g., to fetch callee workflow parameters.
     *
     * New nodes using the file system browser use the
     * {@link #CallWorkflowConnectionConfiguration(NodeCreationConfiguration, String)} constructor, in which case the
     * callee workflow path is managed via a settings model instead of a plain string.
     */
    public CallWorkflowConnectionConfiguration() {
        m_version = Version.VERSION_1;
        m_workflowChooserModel = null;
        m_statusConsumer = null;
        m_connectorPortGroup = null;
    }

    /**
     * @param creationConfig a node's ports might include a file system connector port or the like that provides access
     *            to the location of the callee
     * @param inputPortGroupName the identifier of the port group that contains the callee location input port
     */
    public CallWorkflowConnectionConfiguration(final NodeCreationConfiguration creationConfig,
        final String inputPortGroupName) {
        m_version = Version.VERSION_2;
        final var connectorPortGroup = new ConnectorPortGroup(creationConfig, inputPortGroupName);
        m_connectorPortGroup = connectorPortGroup;
        m_connectionType = CallWorkflowUtil.isHubAuthenticatorConnected(connectorPortGroup)
            ? ConnectionType.HUB_AUTHENTICATION : ConnectionType.FILE_SYSTEM;
        m_workflowChooserModel =
            new SettingsModelWorkflowChooser("calleeWorkflow", inputPortGroupName,
                connectorPortGroup.portsConfiguration());

        m_statusConsumer = new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));
    }

    // save & load

    /**
     * @param settings writes member variables to this object
     */
    protected void saveSettings(final NodeSettingsWO settings) {
        settings.addBoolean("discardJobOnSuccessfulExecution", m_discardJobOnSuccessfulExecution);
        settings.addBoolean("isSynchronous", m_isSynchronous);
        settings.addBoolean("keepFailingJobs", m_keepFailingJobs);
        settings.addString("executionContext", m_executionContext);
        ItemVersionStringPersistor.save(m_itemVersion, settings);
        settings.addString("deploymentId", m_deploymentId);

        if (m_version == Version.VERSION_1) {
            settings.addString("workflow", getWorkflowPath());
        } else if (m_version == Version.VERSION_2 && m_connectionType == ConnectionType.FILE_SYSTEM) {
            m_workflowChooserModel.saveSettingsTo(settings);
            ItemVersionStringPersistor.save(m_itemVersion, settings);
        }

        m_reportConfiguration.save(settings);
        m_loadTimeout.ifPresent(duration -> settings.addInt("loadTimeout", (int)duration.getSeconds()));
        m_fetchWorkflowParametersTimeout
            .ifPresent(duration -> settings.addInt("fetchParametersTimeout", (int)duration.getSeconds()));
        m_backoffPolicy.ifPresent(backoffPolicy -> backoffPolicy.saveToSettings(settings));
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
        } else if (m_version == Version.VERSION_2 && m_connectionType == ConnectionType.FILE_SYSTEM) {
            m_workflowChooserModel.loadSettingsFrom(settings);
        }

        m_isSynchronous = strict ? //
            settings.getBoolean("isSynchronous") : //
            settings.getBoolean("isSynchronous", false);

        // non-strict for backwards compatibility
        m_discardJobOnSuccessfulExecution = settings.getBoolean("discardJobOnSuccessfulExecution", true);
        m_keepFailingJobs = settings.getBoolean("keepFailingJobs", true);
        m_executionContext = settings.getString("executionContext", "");
        m_deploymentId = settings.getString("deploymentId", "");
        m_itemVersion = ItemVersionStringPersistor.load(settings).orElse(ItemVersion.currentState());
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
     * @throws InvalidSettingsException
     */
    protected void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        loadBaseSettings(settings, true);
        m_reportConfiguration.loadInModel(settings);
        ConnectionUtil.validateConfiguration(this);
    }

    // getters & setters

    /**
     * @return Absolute or relative path to the local or remote workflow to execute. For instance
     *         <ul>
     *         <li>/Components/Workflow</li>
     *         <li>someKnimeServerDirectory/path</li>
     *         <li>knime://knime-teamspace/callee</li>
     *         <li>knime://knime.mountpoint/callee</li>
     *         <li>knime://knime.workflow/callee</li>
     *         </ul>
     */
    public String getWorkflowPath() {
        if (m_version == Version.VERSION_1) {
            return m_workflowPath;
        } else {
            // use uri only for
            // - mountpoint connector configured to "Current Mountpoint"
            // - mountpoint connector configured to other mount point "LOCAL"
            // - mountpoint connector configured to teamspace
            // - space connector configured to "Current Space"
            // - in case no connector is present
            if (ConnectionUtil.isRemoteConnection(m_workflowChooserModel.getLocation())) {
                return m_workflowChooserModel.getPath();
            } else {
                // the local workflow backend requires the path in a custom format, or knime uri
                return m_workflowChooserModel.getCalleeKnimeUri()//
                    .map(URI::toString)//
                    .orElse(m_workflowChooserModel.getPath());
            }
        }
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
     * @param workflowChooserModel sets the workflow chooser
     */
    public void setWorkflowChooserModel(final SettingsModelWorkflowChooser workflowChooserModel) {
         m_workflowChooserModel = workflowChooserModel;
    }

    /**
     * Configures the callee workflow chooser settings model by passing the input port object specs to it.
     * @param specs
     * @throws InvalidSettingsException
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
            return m_connectorPortGroup.getConnectorPortIndex();
        default:
            throw new IllegalStateException("Coding error: Unhandled version " + m_version);
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
        if (m_version == Version.VERSION_2 && m_connectionType == ConnectionType.FILE_SYSTEM) {
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
        return Optional.ofNullable(m_connectorPortGroup.portsConfiguration());
    }

    /**
     * @return an error message if the configuration is not suitable to create an {@link IWorkflowBackend}
     */
    public Optional<String> validateForCreateWorkflowBackend() {
        if (m_version == Version.VERSION_1) {
            CallWorkflowUtil.PlainWorkflowPathFormat.validate(getWorkflowPath());
        }
        return Optional.empty();
    }

    /**
     * @return the hub item version of the workflow to call, empty for server and local connections.
     */
    public ItemVersion getItemVersion() {
        return m_itemVersion;
    }

    /**
     * @param itemVersion the itemVersion to set
     */
    public void setItemVersion(final ItemVersion itemVersion) {
        m_itemVersion = itemVersion;
    }

    /**
     * Sets the execution context to be used (only valid for Hub).
     *
     * @param executionContext the ID of the execution context
     */
    public void setExecutionContext(final String executionContext) {
        m_executionContext = executionContext;
    }

    /**
     * Returns the execution context to be used (only valid for Hub).
     *
     * @return the ID of the execution context
     */
    public String getExecutionContext() {
        return m_executionContext;
    }

    /**
     * Sets the authenticator to be used (only valid for Hub).
     *
     * @param authenticator the authenticator of the connected Hub instance.
     */
    public void setHubAuthentication(final AbstractHubAuthenticationPortObjectSpec authenticator) {
        m_hubAuthentication = authenticator;
    }

    /**
     * Returns the authenticator to be used (only valid for Hub).
     *
     * @return authenticator the authenticator of the connected Hub instance.
     */
    public AbstractHubAuthenticationPortObjectSpec getHubAuthentication() {
        return m_hubAuthentication;
    }

    /**
     * Sets the deployment ID to be used (only valid for Hub).
     *
     * @param deploymentId the ID of the deployment.
     */
    public void setDeploymentId(final String deploymentId) {
        m_deploymentId = deploymentId;
    }

    /**
     * Returns the deployment id to be used (only valid for Hub).
     *
     * @return deploymentId the ID of the deployment.
     */
    public String getDeploymentId() {
        return m_deploymentId;
    }

    /**
     * Returns the {@link ConnectionType} of the Call Workflow Node.
     *
     * @return the executionType either Hub Authentication or File System connection type.
     */
    public ConnectionType getConnectionType() {
        return m_connectionType;
    }

    /**
     * Sets the {@link ConnectionType} of the Call Workflow Node.
     *
     * @param connectionType the connectionType to set.
     */
    public void setConnectionType(final ConnectionType connectionType) {
        m_connectionType = connectionType;
    }

    /** @return a copy of this configuration suitable for fetching the parameters of the callee. */
    public CallWorkflowConnectionConfiguration createFetchConfiguration() {

        CallWorkflowConnectionConfiguration result;
        if(m_version == Version.VERSION_1) {
            result = new CallWorkflowConnectionConfiguration();
            result.setWorkflowPath(getWorkflowPath());
        } else {
            result = new CallWorkflowConnectionConfiguration(m_connectorPortGroup.nodeConfiguration(),
                m_connectorPortGroup.connectorPortGroupName());
            result.setHubAuthentication(getHubAuthentication());
            if(m_version == Version.VERSION_2 && m_connectionType == ConnectionType.FILE_SYSTEM) {
                result.setWorkflowChooserModel(getWorkflowChooserModel());
                result.setItemVersion(getItemVersion());
            } else {
                result.setDeploymentId(getDeploymentId());
            }
        }
        result.setBackoffPolicy(getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY));
        result.setConnectionType(getConnectionType());
        result.setDiscardJobOnSuccessfulExecution(true);
        result.setKeepFailingJobs(false);
        result.setFetchParametersTimeout(getFetchParametersTimeout().orElse(Duration.ofSeconds(20)));
        result.setLoadTimeout(getLoadTimeout().orElse(Duration.ofSeconds(20)));
        result.setReportFormat(getReportFormat().orElse(RptOutputFormat.CSV));
        // fetching the parameters should be fast
        result.setSynchronousInvocation(true);
        return result;
    }

    /**
     * Specifies whether a call workflow connects to a file system or a hub authentication connection.
     *
     * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
     */
    public enum ConnectionType {
            /**
             * Hub Authentication connection is used when the Hub Authenticator is used.
             */
            HUB_AUTHENTICATION,
            /**
             * File System connection is used when the File System is used or no connection port is present.
             */
            FILE_SYSTEM
    }
}