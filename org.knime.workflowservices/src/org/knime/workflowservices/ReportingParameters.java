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
 *   Created on Jan 9, 2026 by paulbaernreuther
 */
package org.knime.workflowservices;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.knime.base.node.util.regex.CaseMatching.Persistor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.EnumChoice;
import org.knime.node.parameters.widget.choices.EnumChoicesProvider;
import org.knime.workflowservices.CallWorkflowLayout.TimeoutsSection;
import org.knime.workflowservices.CallWorkflowLayout.WorkflowOrDeploymentSection;
import org.knime.workflowservices.ReportingParameters.ReportingSection;

/**
 * Add this as type of a top-level field using {@link Persistor} with the {@link ReportingParametersPersistor} to save
 * and load the report format from the `reportFormatOrNull` key.
 *
 * @since 5.10
 */
@Layout(ReportingSection.class)
public final class ReportingParameters implements NodeParameters {

    /**
     * The section containing this parameter
     */
    @Section(title = "Reporting")
    @After(WorkflowOrDeploymentSection.class)
    @Before(TimeoutsSection.class)
    public interface ReportingSection {
    }

    /**
     * Default constructor.
     */
    public ReportingParameters() {
        // Default constructor for deserialization
    }

    ReportingParameters(final RptOutputFormat reportFormat) {
        m_createReport = true;
        m_reportFormat = reportFormat;
    }

    @Widget(title = "Create report",
        description = "If checked, the report associated with the remote workflow will be generated and "
            + "put into the output table. The output table "
            + "will contain a column containing the binary content of the report (column type: binary object); "
            + "the column can be further processed, e.g. use a \"Binary Objects to Files\" node to write the "
            + "content to a file, or use a database writer to write the report as BLOB into a database. "
            + "Failures to generate the report (for instance, because no report is attached to the workflow) "
            + "will result in a missing cell.")
    @ValueReference(CreateReport.class)
    boolean m_createReport;

    static final class CreateReport implements BooleanReference {

    }

    @Widget(title = "Report format", description = "Choose the format of the report to be generated.")
    @ChoicesProvider(EnumConstantNameAsTitleChoicesProvider.class)
    @Effect(predicate = CreateReport.class, type = Effect.EffectType.SHOW)
    RptOutputFormat m_reportFormat = RptOutputFormat.PDF;

    static final class EnumConstantNameAsTitleChoicesProvider implements EnumChoicesProvider<RptOutputFormat> {

        @Override
        public List<EnumChoice<RptOutputFormat>> computeState(final NodeParametersInput context) {
            return Arrays.stream(RptOutputFormat.values()).map(e -> new EnumChoice<>(e, e.name())).toList();
        }

    }

    /**
     * Use this to persist {@link ReportingParameters}.
     */
    public static final class ReportingParametersPersistor implements NodeParametersPersistor<ReportingParameters> {

        private static final String REPORT_FORMAT_OR_NULL_CFG_KEY = "reportFormatOrNull";

        @Override
        public ReportingParameters load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var reportFormatOrNull = settings.getString(REPORT_FORMAT_OR_NULL_CFG_KEY, null);
            if (reportFormatOrNull == null) {
                return new ReportingParameters();
            }
            try {
                var reportFormat = RptOutputFormat.valueOf(reportFormatOrNull);
                return new ReportingParameters(reportFormat);
            } catch (IllegalArgumentException e) {
                throw new InvalidSettingsException(createInvalidSettingsExceptionMessage(reportFormatOrNull), e);
            }

        }

        private static String createInvalidSettingsExceptionMessage(final String name) {
            var values = Arrays.stream(RptOutputFormat.class.getEnumConstants()).map(Enum::name)
                .collect(Collectors.joining(", "));
            return String.format("Invalid value '%s'. Possible values: %s", name, values);
        }

        @Override
        public void save(final ReportingParameters param, final NodeSettingsWO settings) {
            if (param.m_createReport) {
                settings.addString(REPORT_FORMAT_OR_NULL_CFG_KEY, param.m_reportFormat.name());
            }
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{REPORT_FORMAT_OR_NULL_CFG_KEY}};
        }

    }

}
