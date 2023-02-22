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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import java.time.Duration;
import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil.FailingJobRetentionPolicy;
import org.knime.workflowservices.connection.util.ConnectionUtil.SuccessfulJobRetentionPolicy;

/**
 * @param <T> see documentation on superclass
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class RemoteCallWorkflowConnectionConfiguration<T extends InvocationTarget>
    extends LocalCallWorkflowConnectionConfiguration<T> {

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
     * @param invocationTarget the workflow or deployment to execute
     */
    protected RemoteCallWorkflowConnectionConfiguration(final T invocationTarget) {
        super(invocationTarget);
    }

    @Override
    protected void saveSettings(final NodeSettingsWO settings) {
        super.saveSettings(settings);
        settings.addBoolean("discardJobOnSuccessfulExecution", m_discardJobOnSuccessfulExecution);
        settings.addBoolean("isSynchronous", m_isSynchronous);
        settings.addBoolean("keepFailingJobs", m_keepFailingJobs);

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
    @Override
    protected void loadBaseSettings(final NodeSettingsRO settings, final boolean strict)
        throws InvalidSettingsException {
        super.loadBaseSettings(settings, strict);

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

    @Override
    public void validateConfigurationForModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.validateConfigurationForModel(settings);
        if (!settings.containsKey("isSynchronous")) {
            throw new InvalidSettingsException(
                "Settings do not specify whether callee invocation should be synchronous.");
        }
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
    public LocalCallWorkflowConnectionConfiguration setBackoffPolicy(final BackoffPolicy policy) {
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
    public LocalCallWorkflowConnectionConfiguration setLoadTimeout(final Duration timeout) {
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
    public LocalCallWorkflowConnectionConfiguration setFetchParametersTimeout(final Duration timeout) {
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
    public LocalCallWorkflowConnectionConfiguration
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
    public LocalCallWorkflowConnectionConfiguration setKeepFailingJobs(final boolean keepFailingJobs) {
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
    public LocalCallWorkflowConnectionConfiguration setSynchronousInvocation(final boolean isSynchronous) {
        m_isSynchronous = isSynchronous;
        return this;
    }
}
