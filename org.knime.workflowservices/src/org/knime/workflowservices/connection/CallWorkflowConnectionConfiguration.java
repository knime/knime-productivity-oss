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

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.workflowservices.connection.IServerConnection.FailingJobRetentionPolicy;
import org.knime.workflowservices.connection.IServerConnection.SuccessfulJobRetentionPolicy;
import org.knime.workflowservices.connection.util.BackoffPolicy;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class CallWorkflowConnectionConfiguration {

    /**
     * Check if the user entered path is a path. It should be absolute (start with '/') or relative (start with '..')
     *
     * @param workflowPath the path to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidWorkflowPath(final String workflowPath) {
        return StringUtils.startsWithAny(workflowPath, "/", "..") && !workflowPath.endsWith("/");
    }

    /**
     * Generates an error message for why {@link #isValidWorkflowPath(String)} returns false.
     *
     * @param workflowPath
     * @return a user-facing error message in case an invalid workflow path is entered
     */
    public static String invalidWorkflowPathMessage(final String workflowPath) {
        return String.format("Invalid workflow path: \"%s\". Path must start with \"/\" or \"..\" and must not end with \"/\"", workflowPath);
    }

    private Optional<BackoffPolicy> m_backoffPolicy = Optional.empty();

    private boolean m_discardJobOnSuccessfulExecution = true;

    private boolean m_isSynchronous;

    private boolean m_keepFailingJobs = true;

    private Optional<Duration> m_loadTimeout = Optional.empty();

    private String m_workflowPath;

    public Optional<BackoffPolicy> getBackoffPolicy() {
        return m_backoffPolicy;
    }

    public CallWorkflowConnectionConfiguration setBackoffPolicy(final BackoffPolicy policy) {
        m_backoffPolicy = Optional.of(policy);
        return this;
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
    public CallWorkflowConnectionConfiguration setDiscardJobOnSuccessfulExecution(final boolean discardJobOnSuccessfulExecution) {
        m_discardJobOnSuccessfulExecution = discardJobOnSuccessfulExecution;
        return this;
    }

    public FailingJobRetentionPolicy getFailingJobPolicy() {
        return isKeepFailingJobs() //
            ? FailingJobRetentionPolicy.KEEP_FAILING_JOBS //
            : FailingJobRetentionPolicy.DELETE_FAILING_JOBS;
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
     * @return time after which to give up loading the workflow referenced by {@link #getWorkflowPath()}
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
     * @return what to do with successful remote jobs, e.g., keep or delete
     */
    public SuccessfulJobRetentionPolicy getSuccessfulJobPolicy() {
        return isDiscardJobOnSuccessfulExecution() //
            ? SuccessfulJobRetentionPolicy.DELETE_SUCCESSFUL_JOBS //
            : SuccessfulJobRetentionPolicy.KEEP_SUCCESSFUL_JOBS;
    }

    public boolean isSynchronousInvocation() {
        return m_isSynchronous;
    }

    public CallWorkflowConnectionConfiguration setSynchronousInvocation(final boolean isSynchronous) {
        m_isSynchronous = isSynchronous;
        return this;
    }

    /**
     * @return a reference to a local or remote workflow to execute
     * @see CallWorkflowConnectionConfiguration#isValidWorkflowPath(String)
     */
    public String getWorkflowPath() {
        return m_workflowPath;
    }

    /**
     * @param workflowPath {@link #getWorkflowPath()}
     * @return this
     */
    public CallWorkflowConnectionConfiguration setWorkflowPath(final String workflowPath) {
        m_workflowPath = workflowPath;
        return this;
    }

    /**
     * Populate the fields from the settings object.
     */
    private void loadBaseSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_backoffPolicy = BackoffPolicy.loadFromSettings(settings);
        m_discardJobOnSuccessfulExecution = settings.getBoolean("discardJobOnSuccessfulExecution", true);
        m_isSynchronous = settings.getBoolean("isSynchronous");
        m_keepFailingJobs = settings.getBoolean("keepFailingJobs");
        m_workflowPath = settings.getString("workflow", "");

        try {
            m_loadTimeout = Optional.of(Duration.ofSeconds(Math.max(settings.getInt("loadTimeout"), 0)));
        } catch (InvalidSettingsException e) { // NOSONAR backward compatibility
            m_loadTimeout = Optional.empty();
        }
    }

    /**
     * Initializes members from the given settings.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    protected void loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        loadBaseSettings(settings);
        // only accept path values, no protocol (also no knime://) as the path will be turned into a
        // knime (workflow or mountpoint) relative path
        if (!isValidWorkflowPath(m_workflowPath)) {
            throw new InvalidSettingsException(invalidWorkflowPathMessage(m_workflowPath));
        }
    }

    /**
     * Load settings and also do validation that will prevent the dialog from closing if invalid values are entered.
     *
     * @param settings
     * @throws InvalidSettingsException
     */
    protected void loadSettingsInDialog(final NodeSettingsRO settings) throws InvalidSettingsException {
        loadBaseSettings(settings);
    }

    /**
     * @param settings writes member variables to this object
     */
    protected void saveSettings(final NodeSettingsWO settings) {
        settings.addBoolean("discardJobOnSuccessfulExecution", m_discardJobOnSuccessfulExecution);
        settings.addBoolean("isSynchronous", m_isSynchronous);
        settings.addBoolean("keepFailingJobs", m_keepFailingJobs);
        settings.addString("workflow", m_workflowPath);

        m_loadTimeout.ifPresent(duration -> settings.addInt("loadTimeout", (int)duration.getSeconds()));
        m_backoffPolicy.ifPresent(backoffPolicy -> backoffPolicy.saveToSettings(settings));
    }
}