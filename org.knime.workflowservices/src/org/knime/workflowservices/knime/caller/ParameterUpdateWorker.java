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
 *   Created on 28 Sep 2021 by carlwitt
 */
package org.knime.workflowservices.knime.caller;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * Asynchronously fetch the input and output parameter groups from a local or remote workflow.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class ParameterUpdateWorker extends SwingWorkerWithContext<WorkflowParameters, Void> {

    private final CallWorkflowConnectionConfiguration m_connectionConfiguration;

    private final Consumer<String> m_errorDisplay;

    private final IServerConnection m_serverConnection;

    private Consumer<WorkflowParameters> m_whenDone;

    /**
     *
     * @param workflowPath path of the workflow to load, e.g., "/Some/Folder/the_workflow"
     * @param errorDisplay something capable of displaying error messages
     * @param loadTimeout maximum time to wait for the remote side
     * @param serverConnection local or remote connection
     * @param whenDone callback for the retrieved information about the subworkflow
     * @throws InvalidSettingsException if the configuration cannot be used to instantiate an {@link IWorkflowBackend}
     *             from the serverConnection.
     */
    @Deprecated(since = "4.7.1")
    public ParameterUpdateWorker(final String workflowPath, final Consumer<String> errorDisplay, final Duration loadTimeout,
        final IServerConnection serverConnection, final Consumer<WorkflowParameters> whenDone,
        final Supplier<CallWorkflowConnectionConfiguration> configuration) throws InvalidSettingsException {

        if (serverConnection == null) {
            throw new IllegalArgumentException(
                "Cannot connect to server. Please re-execute upstream KNIME Server Connector node.");
        }

        m_errorDisplay = errorDisplay;

        m_connectionConfiguration = configuration.get();
        m_connectionConfiguration.setLoadTimeout(loadTimeout);
        m_connectionConfiguration.setWorkflowPath(workflowPath);
        m_connectionConfiguration.setKeepFailingJobs(false);
        IServerConnection.validateConfiguration(m_connectionConfiguration, serverConnection);

        m_serverConnection = serverConnection;

        m_whenDone = whenDone;
    }

    /**
     * @param configuration where to fetch parameters from
     * @param errorDisplay where to show problems
     * @param whenDone where to deliver the results
     */
    public ParameterUpdateWorker(final CallWorkflowConnectionConfiguration configuration,
        final Consumer<String> errorDisplay, final Consumer<WorkflowParameters> whenDone) {
        m_errorDisplay = errorDisplay;
        m_connectionConfiguration = configuration;
        m_serverConnection = null;
        m_whenDone = whenDone;
    }

    @Override
    protected WorkflowParameters doInBackgroundWithContext() throws Exception {
        if (isEmptyCallee()) {
            throw new IOException("Please select an execution target.");
        }
        ConnectionUtil.validateConfiguration(m_connectionConfiguration);
        try (IWorkflowBackend backend = createWorkflowBackend()) {
            var inputResourceDescription = backend.getInputResourceDescription();
            var outputResourceDescription = backend.getOutputResourceDescription();
            return new WorkflowParameters(inputResourceDescription, outputResourceDescription);
        }
    }

    private boolean isEmptyCallee() {
        if (m_connectionConfiguration.getConnectionType() == ConnectionType.FILE_SYSTEM) {
            return StringUtils.isEmpty(m_connectionConfiguration.getWorkflowPath());
        } else {
            return StringUtils.isEmpty(m_connectionConfiguration.getDeploymentId());
        }
    }

    private IWorkflowBackend createWorkflowBackend() throws Exception {
        if (ObjectUtils.isEmpty(m_serverConnection)) {
            return ConnectionUtil.createWorkflowBackend(m_connectionConfiguration);
        } else {
            return m_serverConnection.createWorkflowBackend(m_connectionConfiguration);
        }
    }

    @Override
    protected void doneWithContext() {
        if (!isCancelled()) {
            try {
                var remoteWorkflowProperties = get();
                if (remoteWorkflowProperties != null) {
                    m_whenDone.accept(remoteWorkflowProperties);
                }
            } catch (InterruptedException | CancellationException e) {
                NodeLogger.getLogger(getClass()).debug(e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                NodeLogger.getLogger(getClass()).debug(e);
                m_errorDisplay.accept(ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }
}