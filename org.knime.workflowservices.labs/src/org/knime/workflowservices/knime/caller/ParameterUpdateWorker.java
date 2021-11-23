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

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.knime.core.util.SwingWorkerWithContext;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Asynchronously fetch the input and output parameter groups from a local or remote workflow.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
final class ParameterUpdateWorker extends SwingWorkerWithContext<CalleeWorkflowProperties, Void> {

    private final CallWorkflowConnectionConfiguration m_connectionConfiguration;

    private final Consumer<String> m_errorDisplay;

    private final IServerConnection m_serverConnection;

    private Consumer<CalleeWorkflowProperties> m_whenDone;

    /**
     *
     * @param workflowPath path of the workflow to load, e.g., "/Some/Folder/the_workflow"
     * @param errorDisplay something capable of displaying error messages
     * @param loadTimeout maximum time to wait for the remote side
     * @param serverConnection local or remote connection
     * @param whenDone callback for the retrieved information about the subworkflow
     */
    ParameterUpdateWorker(final String workflowPath, final Consumer<String> errorDisplay, final Duration loadTimeout,
        final IServerConnection serverConnection, final Consumer<CalleeWorkflowProperties> whenDone,
        final Supplier<CallWorkflowConnectionConfiguration> configuration) {

        if (serverConnection == null) {
            throw new IllegalArgumentException("Server connection must not be null.");
        }

        if (workflowPath.isBlank() || !CallWorkflowConnectionConfiguration.isValidWorkflowPath(workflowPath)) {
            throw new IllegalArgumentException(
                CallWorkflowConnectionConfiguration.invalidWorkflowPathMessage(workflowPath));
        }
        m_errorDisplay = errorDisplay;
        errorDisplay.accept("");

        m_connectionConfiguration = configuration.get();
        m_connectionConfiguration.setLoadTimeout(loadTimeout);
        m_connectionConfiguration.setWorkflowPath(workflowPath);
        m_connectionConfiguration.setKeepFailingJobs(false);

        m_serverConnection = serverConnection;

        m_whenDone = whenDone;

    }

    @Override
    protected CalleeWorkflowProperties doInBackgroundWithContext() throws Exception {

        try (IWorkflowBackend backend = m_serverConnection.createWorkflowBackend(m_connectionConfiguration)) {
            if (backend != null) {
                var inputResourceDescription = backend.getInputResourceDescription();
                var outputResourceDescription = backend.getOutputResourceDescription();
                return new CalleeWorkflowProperties(inputResourceDescription, outputResourceDescription);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void doneWithContext() {
        if (!isCancelled()) {
            try {
                var remoteWorkflowProperties = get();
                if (remoteWorkflowProperties != null) {
                    m_whenDone.accept(remoteWorkflowProperties);
                    m_errorDisplay.accept("");
                }
            } catch (InterruptedException | CancellationException e) {
                // do nothing
            } catch (ExecutionException e) {
                m_errorDisplay.accept(getParametersError(e));
            }
        }
    }

    /**
     * @param e the exception that was thrown when calling {@link #get()} in the result processing in
     *            {@link #doneWithContext()}
     * @return an error message for the node dialog to display
     */
    private static String getParametersError(final Exception e) {
        return ServerConnectionUtil.handle(e).getLeft();
    }
}