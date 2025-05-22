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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.api.Persistor;
import org.knime.core.webui.node.dialog.defaultdialog.widget.RichTextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.workflowservices.knime.caller.WorkflowParameter;

/**
 * Settings class for Workflow Output node.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // webui not API yet
final class WorkflowOutputSettings extends WorkflowBoundaryConfiguration {

    @Widget(title = "Parameter name", description = """
            The parameter name is supposed to be unique, but this is not enforced.
            In case multiple <i>Workflow Output</i> nodes define the same
            parameter name, KNIME will make them unique by appending the node's node ID,
            e.g., "output-table" becomes "output-table-7".
            """)
    String m_parameterName = "output-parameter";

    @Widget(title = "Description", description = """
            The description for the workflow output parameter describing the purpose of the parameter.
            """)
    @RichTextInputWidget
    @Persistor(OptionalParameterDescription.class)
    String m_parameterDescription;

    @Override
    String getParameterName() {
        return m_parameterName;
    }

    @Override
    WorkflowBoundaryConfiguration setParameterName(final String parameterName) throws InvalidSettingsException {
        m_parameterName = WorkflowParameter.validateParameterName(parameterName);
        return this;
    }

    @Override
    String getParameterDescription() {
        return m_parameterDescription;
    }

    // method for buildworkflows
    static void saveToNodeSettings(final NodeSettingsWO settings, final String parameterName,
        final String parameterDescription) {
        final var config = new WorkflowOutputSettings();
        config.m_parameterName = parameterName;
        config.m_parameterDescription = parameterDescription;
        DefaultNodeSettings.saveSettings(WorkflowOutputSettings.class, config, settings);
    }

}
