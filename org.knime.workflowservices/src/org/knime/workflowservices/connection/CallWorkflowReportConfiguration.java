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

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;

/**
 * Manages a report format configuration option. Used in {@link CallWorkflowConnectionConfiguration}.
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class CallWorkflowReportConfiguration {

    /** @see #getReportFormat() */
    private Optional<RptOutputFormat> m_reportFormat = Optional.empty();

    // save & load

    /**
     * @param settings to save this configuration into
     */
    public void save(final NodeSettingsWO settings) {
        settings.addString("reportFormatOrNull", Objects.toString(m_reportFormat.orElse(null), null));
    }

    /**
     * Called in the node model to read the information entered in the node dialog. This will throw exceptions if
     * something is wrong with the configuration.
     *
     * @param settings settings produced by the node dialog
     * @throws InvalidSettingsException
     */
    public void loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        loadReportFormat(settings);
    }

    /**
     * Called in the node dialog to read persisted configuration. This will use default values if something is wrong
     * with the configuration.
     *
     * @param settings settings as persisted, e.g., to disk
     */
    public void loadInDialog(final NodeSettingsRO settings) {
        try {
            loadReportFormat(settings);
        } catch (InvalidSettingsException e) {
            m_reportFormat = Optional.empty();
        }
    }

    /**
     * @param settings to load from
     * @throws InvalidSettingsException if the settings contains an invalid report format (not an enum from
     *             {@link RptOutputFormat}).
     */
    protected void loadReportFormat(final NodeSettingsRO settings) throws InvalidSettingsException {
        var reportStringOrNull = settings.getString("reportFormatOrNull", null); // added in 3.2
        if (StringUtils.isBlank(reportStringOrNull)) {
            reportStringOrNull = null;
        }
        if (reportStringOrNull == null) {
            setReportFormat(null);
        } else {

            try {
                var format = RptOutputFormat.valueOf(reportStringOrNull);
                setReportFormat(format);
            } catch (IllegalArgumentException ex) {
                throw new InvalidSettingsException("Settings contains invalid report format: " + reportStringOrNull);
            }
        }
    }

    // getters and setters

    /** @return the format of the report to be generated in the callee workflow */
    public Optional<RptOutputFormat> getReportFormat() {
        return m_reportFormat;
    }

    /**
     * @param reportFormatOrNull the report format or null if no report should be generated
     */
    public void setReportFormat(final RptOutputFormat reportFormatOrNull) {
        m_reportFormat = Optional.ofNullable(reportFormatOrNull);
    }

}