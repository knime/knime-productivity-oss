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
 *   Created on Nov 10, 2020 by wiswedel
 */
package org.knime.workflowservices.connection;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend;

/**
 * A connection provides {@link IWorkflowBackend}s, where the backend of a particular workflow allows controlling its
 * execution. A connection also
 *
 * Represents the connection details to a server, whereby 'server' could also be local execution.
 *
 * Class hierarchy came into existence as part of AP-15518 (rewrite node based on 4.3 file handling concept).
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @noreference
 */
public interface IServerConnection extends Closeable {

    // TODO move policies into separate classes
    /**
     * Enum policy deciding what to do with failing jobs.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    enum FailingJobRetentionPolicy {
            /**
             * Keep failing jobs.
             */
            KEEP_FAILING_JOBS,
            /**
             * Delete failing jobs.
             */
            DELETE_FAILING_JOBS;
    }

    /**
     * Enum policy deciding what to do with successful jobs.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    enum SuccessfulJobRetentionPolicy {
            /**
             * Keep failing jobs.
             */
            KEEP_SUCCESSFUL_JOBS,
            /**
             * Delete failing jobs.
             */
            DELETE_SUCCESSFUL_JOBS;
    }

    /** Default value for connection and read timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The default timeout to use when loading a remote workflow.
     */
    public static final Duration DEFAULT_LOAD_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Creates the backend to use. The configuration must be valid according to
     * {@link #validate(CallWorkflowConnectionConfiguration)}.
     *
     * @param configuration contains timeouts, workflow path, etc.
     * @return The backend, not null.
     * @throws Exception ...
     * @see #validate(CallWorkflowConnectionConfiguration)
     */
    IWorkflowBackend createWorkflowBackend(CallWorkflowConnectionConfiguration configuration) throws Exception;

    /**
     * Check whether the given configuration can be passed to
     * {@link #createWorkflowBackend(CallWorkflowConnectionConfiguration)}
     *
     * @param configuration to check
     * @return a user-facing error message in case an invalid configuration is given, empty optional if the
     *         configuration is valid
     */
    default Optional<String> validate(final CallWorkflowConnectionConfiguration configuration) {

        if (configuration == null) {
            return Optional.of("Configuration is null");
        }

        // check workflow path
        var problem = configuration.validateForCreateWorkflowBackend();
        if (problem.isPresent()) {
            return problem;
        }

        // check report format
        boolean isHtmlFormat = configuration.getReportFormat().map(fmt -> fmt == RptOutputFormat.HTML).orElse(false);
        if (isHtmlFormat) {
            return Optional.of("HTML report format is not supported.");
        }

        // everything ok
        return Optional.empty();
    }

    /**
     * Called from the dialog for some label.
     *
     * @return Host name.
     */
    default String getHost() {
        throw new IllegalStateException("Not to be called on  " + getClass().getSimpleName());
    }

    /**
     * Used by dialog to list all available workflows.
     *
     * @return a list of workflows
     * @throws ListWorkflowFailedException if an error occurs
     */
    default List<String> listWorkflows() throws ListWorkflowFailedException {
        throw new IllegalStateException("Not to be called on  " + getClass().getSimpleName());
    }

    @Override
    default void close() throws IOException {
    }

    /** Checked exception thrown by {@link IServerConnection#listWorkflows()}. */
    @SuppressWarnings("serial")
    public final class ListWorkflowFailedException extends Exception {

        /**
         * @param message
         * @param cause
         */
        public ListWorkflowFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }

        /**
         * @param cause
         */
        public ListWorkflowFailedException(final Throwable cause) {
            super(cause);
        }
    }

    /**
     * @param configuration to validate
     * @param connection the connection to use for validation
     * @throws InvalidSettingsException if the given connection is not null and the connection's validation method
     *             returns an error message
     */
    public static void validateConfiguration(final CallWorkflowConnectionConfiguration configuration,
        final IServerConnection connection) throws InvalidSettingsException {
        if (connection != null) {
            var error = connection.validate(configuration);
            if (error.isPresent()) {
                throw new InvalidSettingsException(error.get());
            }
        }
    }

}
