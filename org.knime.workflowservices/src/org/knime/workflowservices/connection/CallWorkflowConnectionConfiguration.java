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
import java.util.Optional;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend;
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

    /** @see #getWorkflowPath() */
    private String m_workflowPath;

    /** @see #getReportFormat() */
    private final CallWorkflowReportConfiguration m_reportConfiguration = new CallWorkflowReportConfiguration();

    // save & load

    /**
     * @param settings writes member variables to this object
     */
    protected void saveSettings(final NodeSettingsWO settings) {
        settings.addBoolean("discardJobOnSuccessfulExecution", m_discardJobOnSuccessfulExecution);
        settings.addBoolean("isSynchronous", m_isSynchronous);
        settings.addBoolean("keepFailingJobs", m_keepFailingJobs);
        settings.addString("workflow", m_workflowPath);
        m_reportConfiguration.save(settings);
        m_loadTimeout.ifPresent(duration -> settings.addInt("loadTimeout", (int)duration.getSeconds()));
        m_fetchWorkflowParametersTimeout
            .ifPresent(duration -> settings.addInt("fetchParametersTimeout", (int)duration.getSeconds()));
        m_backoffPolicy.ifPresent(backoffPolicy -> backoffPolicy.saveToSettings(settings));
        m_reportConfiguration.save(settings);
    }

    /**
     * Populate the fields from the settings object.
     */
    private void loadBaseSettings(final NodeSettingsRO settings, final boolean strict) throws InvalidSettingsException {
        m_workflowPath = strict ? //
            settings.getString("workflow") : //
            settings.getString("workflow", "");
        m_isSynchronous = strict ? //
            settings.getBoolean("isSynchronous") : //
            settings.getBoolean("isSynchronous", false);
        m_discardJobOnSuccessfulExecution = strict ? //
            settings.getBoolean("discardJobOnSuccessfulExecution") : //
            settings.getBoolean("discardJobOnSuccessfulExecution", true);
        m_keepFailingJobs = strict ? //
            settings.getBoolean("keepFailingJobs") : //
            settings.getBoolean("keepFailingJobs", true);
        m_backoffPolicy = BackoffPolicy.loadFromSettings(settings);

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
        return m_workflowPath;
    }

    /**
     * @param workflowPath see {@link #getWorkflowPath()}
     * @return this
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
     * @return if true, no job status polling takes place ("short duration" option in {@link CallWorkflowConnectionControls})
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

}