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
package org.knime.workflowservices.knime.callee;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObjectSpec;

/**
 * TODO make final
 * Common superclass for {@link WorkflowInputNodeDialog} and {@link WorkflowOutputNodeDialog}.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public abstract class WorkflowBoundaryNodeDialog extends NodeDialogPane {

    private final ParameterPanel m_parameterPanel;
    private final String m_defParameterName;

    /**
     * @param portConfig initial ports on this input/output node
     * @param defParameterName Name to be used as default for the parameter name, e.g. "input-parameter"
     */
    WorkflowBoundaryNodeDialog(final PortsConfiguration portConfig, final String defParameterName) {
        m_defParameterName = defParameterName;
        m_parameterPanel = new ParameterPanel(portConfig.getOutputPorts()[0], defParameterName);
        final var settingsPanel = new JPanel(new GridBagLayout());
        final var constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.gridx = 0;

        constraints.gridy = 1;
        settingsPanel.add(m_parameterPanel, constraints);

        addTab("Callee Workflow Parameter", settingsPanel);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        var parameterName = m_parameterPanel.getParameterName();
        new WorkflowBoundaryConfiguration(m_defParameterName).setParameterName(parameterName).saveSettingsTo(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        var conf = new WorkflowBoundaryConfiguration(m_defParameterName);
        String paramName;
        try {
            conf.loadSettingsFrom(settings);
            paramName = conf.getParameterName();
        } catch (final InvalidSettingsException e) { // NOSONAR
            paramName = m_defParameterName;
        }
        m_parameterPanel.setParameterName(paramName);
    }

}