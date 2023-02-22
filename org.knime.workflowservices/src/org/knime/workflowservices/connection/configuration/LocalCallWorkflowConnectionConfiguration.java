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
package org.knime.workflowservices.connection.configuration;

import java.util.Optional;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend;

/**
 * Configuration for local workflow execution contains only an invocation target and report settings.
 *
 * @param <T> the invocation target type defines the format in which the workflow to execute is specified
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class LocalCallWorkflowConnectionConfiguration<T extends InvocationTarget> {

    /** Specifies the workflow or deployment to execute. */
    protected final T m_invocationTarget;

    /** @see #getReportFormat() */
    protected final CallWorkflowReportConfiguration m_reportConfiguration = new CallWorkflowReportConfiguration();

    /**
     * @param invocationTarget the workflow or deployment to execute
     */
    protected LocalCallWorkflowConnectionConfiguration(final T invocationTarget) {
        m_invocationTarget = invocationTarget;
    }

    // save & load

    /**
     * @param settings writes member variables to this object
     */
    protected void saveSettings(final NodeSettingsWO settings) {
        m_invocationTarget.saveSettings(settings);
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
        m_invocationTarget.loadSettings(settings);
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
    protected void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        loadBaseSettings(settings, true);
        m_reportConfiguration.loadInModel(settings);

        // check workflow path
        var problem = validateForCreateWorkflowBackend();
        if (problem.isPresent()) {
            throw new InvalidSettingsException(problem.get());
        }

        // check report format
        boolean isHtmlFormat = getReportFormat().map(fmt -> fmt == RptOutputFormat.HTML).orElse(false);
        if (isHtmlFormat) {
            throw new InvalidSettingsException("HTML report format is not supported.");
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
        m_invocationTarget.validate(settings);
    }

    /**
     * @return an error message if the configuration is not suitable to create an {@link IWorkflowBackend}
     */
    public Optional<String> validateForCreateWorkflowBackend() {
        return m_invocationTarget.validateForCreateWorkflowBackend();
    }

    /**
     * @return the workflow or deployment to execute
     */
    public T getInvocationTarget() {
        return m_invocationTarget;
    }

//    /**
//     * @param target see {@link #getInvocationTarget()}
//     * @return this for fluid API
//     */
//    public LocalCallWorkflowConnectionConfiguration<T> setInvocationTarget(final T target) {
//        m_invocationTarget = target;
//        return this;
//    }

    /** @return the format of the report to be generated in the callee workflow */
    public Optional<RptOutputFormat> getReportFormat() {
        return m_reportConfiguration.getReportFormat();
    }

    /**
     * @param reportFormatOrNull the report format or null if no report should be generated
     * @return this for fluent API
     */
    public LocalCallWorkflowConnectionConfiguration<T> setReportFormat(final RptOutputFormat reportFormatOrNull) {
        m_reportConfiguration.setReportFormat(reportFormatOrNull);
        return this;
    }
}