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
 *   Created on 7 Jul 2022 by carlwitt
 */
package org.knime.workflowservices;

import java.util.Map;
import java.util.Optional;

import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend.ReportGenerationException;
import org.knime.workflowservices.IWorkflowBackend.WorkflowState;

import jakarta.json.JsonValue;

/**
 * Summarizes the results of a workflow invocation (remote or local) via a {@link IWorkflowBackend}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class BackendExecutionResult {

    /** {@link #getWorkflowState()} */
    private final WorkflowState m_workflowState;

    /** {@link #getJsonResults()} */
    private final Map<String, JsonValue> m_jsonResults;

    /** {@link #getErrorMessage()} */
    private final Optional<String> m_errorMessage;

    /** {@link #getReport()} */
    private final Optional<byte[]> m_report;

    /** {@link #getReportGenerationException()} */
    private final Optional<ReportGenerationException> m_reportGenerationException;

    /** {@link #getElapsedTimeMs()} */
    private final long m_elapsedTimeMs;

    /**
     * @param errorMessage {@link #getErrorMessage()}
     * @param workflowState {@link #getWorkflowState()}
     * @param elapsedTimeMs {@link #getElapsedTimeMs()}
     */
    public BackendExecutionResult(final String errorMessage, final WorkflowState workflowState,
        final long elapsedTimeMs) {
        m_jsonResults = Map.of();
        m_report = Optional.empty();
        m_reportGenerationException = Optional.empty();
        m_errorMessage = Optional.ofNullable(errorMessage);
        m_workflowState = workflowState;
        m_elapsedTimeMs = elapsedTimeMs;
    }

    /**
     * @param jsonResults {@link #getJsonResults()}
     * @param report {@link #getReport()}
     * @param reportException {@link #getReportGenerationException()}
     * @param errorMessage {@link #getErrorMessage()}
     * @param workflowState {@link #getWorkflowState()}
     * @param elapsedTimeMs {@link #getElapsedTimeMs()}
     */
    public BackendExecutionResult(final Map<String, JsonValue> jsonResults, final byte[] report,
        final ReportGenerationException reportException, final String errorMessage, final WorkflowState workflowState,
        final long elapsedTimeMs) {
        m_jsonResults = jsonResults;
        m_report = Optional.ofNullable(report);
        m_reportGenerationException = Optional.ofNullable(reportException);
        m_errorMessage = Optional.ofNullable(errorMessage);
        m_workflowState = workflowState;
        m_elapsedTimeMs = elapsedTimeMs;
    }

    /** @return The value returned by {@link IWorkflowBackend#execute(Map)} */
    public WorkflowState getWorkflowState() {
        return m_workflowState;
    }

    /** @return The result of {@link IWorkflowBackend#getOutputValues()}. */
    public Map<String, JsonValue> getJsonResults() {
        return m_jsonResults;
    }

    /**
     * @return A message describing exceptions during the retrieval of the execution result of the workflow. This does
     *         NOT include exceptions during report generation, which can be retrieved using
     *         {@link #getReportGenerationException()}.
     */
    public Optional<String> getErrorMessage() {
        return m_errorMessage;
    }

    /**
     * @return The result of {@link IWorkflowBackend#generateReport(RptOutputFormat)}. If this is empty, report
     *         generation was not requested, or {@link #getReportGenerationException()} is present.
     */
    public Optional<byte[]> getReport() {
        return m_report;
    }

    /**
     * @return Exception thrown during {@link IWorkflowBackend#generateReport(RptOutputFormat)}. If this is present,
     *         {@link #getReport()} is empty.
     */
    public Optional<ReportGenerationException> getReportGenerationException() {
        return m_reportGenerationException;
    }

    /** @return The number of milliseconds elapsed during {@link IWorkflowBackend#execute(Map)}. */
    public long getElapsedTimeMs() {
        return m_elapsedTimeMs;
    }

}