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
 *   Created on 20 May 2025 by Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
package org.knime.workflowservices.knime.callee;

import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.internal.WorkflowIOParameterNameValidation;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.widget.text.RichTextInputWidget;
import org.knime.node.parameters.widget.text.TextInputWidget;

/**
 * Settings class for Workflow Input node.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // webui not API yet
final class WorkflowInputSettings extends WorkflowBoundaryConfiguration {

    @Widget(title = "Parameter name", description = """
            The parameter name is supposed to be unique, but this is not enforced.
            In case multiple <i>Workflow Input</i> nodes define the same
            parameter name, KNIME will make them unique by appending the node's node ID,
            e.g., "input-table" becomes "input-table-7".
            """)
    @TextInputWidget(patternValidation = WorkflowIOParameterNameValidation.class)
    String m_parameterName = "input-parameter";

    @Widget(title = "Description", description = """
            The description for the workflow input parameter describing the purpose of the parameter.
            """)
    @RichTextInputWidget
    @Persistor(OptionalParameterDescription.class)
    String m_parameterDescription;

    @Override
    String getParameterName() {
        return m_parameterName;
    }

    @Override
    String getParameterDescription() {
        return m_parameterDescription;
    }

    // method for buildworkflows
    static void saveToNodeSettings(final NodeSettingsWO settings, final String parameterName,
        final String parameterDescription) {
        final var config = new WorkflowInputSettings();
        config.m_parameterName = parameterName;
        config.m_parameterDescription = parameterDescription;
        NodeParametersUtil.saveSettings(WorkflowInputSettings.class, config, settings);
    }
}
