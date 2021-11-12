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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.context.ports.PortGroupConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.workflowservices.knime.CalleeWorkflowData;

/**
 * Takes the set of inputs of a subworkflow (parameter name, type) and the list of input ports of a node (type).
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
final class ParameterMappingPanel extends JPanel {
    private static final long serialVersionUID = 1524479533536582657L;

    ParameterMappingPanel() {
        super(new GridBagLayout());
        final var gbcAdvancedSettings = new GridBagConstraints();
        gbcAdvancedSettings.anchor = GridBagConstraints.LINE_START;
        gbcAdvancedSettings.gridx = gbcAdvancedSettings.gridy = 0;
        gbcAdvancedSettings.weightx = 0;
        gbcAdvancedSettings.weighty = 0;
        gbcAdvancedSettings.gridwidth = 2;
        gbcAdvancedSettings.fill = GridBagConstraints.NONE;
        gbcAdvancedSettings.insets = new Insets(5, 50, 5, 5);
    }

    /**
     * Rebuild the panel contents to display the mapping between Call Workflow node ports and callee workflow
     * parameters.
     *
     * @param props input and output parameters of the callee workflow
     * @param inPorts in order they are assigned to the callee workflow input parameters
     * @param outPorts in order they are assigned to the callee workflow output parameters
     */
    void update(final CalleeWorkflowProperties props, final PortGroupConfiguration inPorts,
        final PortGroupConfiguration outPorts) {

        this.removeAll();
        var gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;

        if (props.getInputNodes().isEmpty() && props.getOutputNodes().isEmpty()) {
            this.add(new JLabel(
                "The selected callee workflow defines neither compatible input parameters nor compatible output parameters."),
                gbc);

        }
        if (!props.getInputNodes().isEmpty()) {

            this.add(new JLabel("Input Parameter Mapping"), gbc);
            gbc.gridy++;
            this.add(createBoundaryPanel(props.getInputNodes(), inPorts.getInputPorts(), false), gbc);
            gbc.gridy++;

        }
        if (!props.getOutputNodes().isEmpty()) {
            this.add(new JLabel("Output Parameter Mapping"), gbc);
            gbc.gridy++;
            this.add(createBoundaryPanel(props.getOutputNodes(), outPorts.getOutputPorts(), true), gbc);

        }

        repaint();
        revalidate();
    }

    /**
     * Creates a panel that lists the mappings from Call Workflow input ports to callee workflow input parameters or the
     * mappings from the callee workflow output parameters to the Call Workflow output ports.
     *
     * @param declaredParameters a list of workflow parameters (either input or output)
     * @param availablePorts the ports on the Call Workflow node (either input or output)
     * @param parameterLabelFirst whether to put the callee workflow parameter first (for output parameters) or the Call
     *            Workflow node port first (for input parameters)
     */
    private static JPanel createBoundaryPanel(final List<CalleeWorkflowData> declaredParameters,
        final PortType[] availablePorts, final boolean parameterLabelFirst) {
        var resultPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 15);

        String[] portLabels = IntStream.range(0, availablePorts.length)
            .mapToObj(i -> String.format("Port %d (%s)", i + 1, availablePorts[i].getName()))//
            .toArray(String[]::new);

        // per default: map the i-th input port to the i-th input parameter
        // that is: for the i-th combobox preselect the i-th element
        var preSelectIndex = 0;

        gbc.gridy = 0;

        // for each input port group
        for (CalleeWorkflowData portDesc : declaredParameters) {

            gbc.gridx = parameterLabelFirst ? 0 : 2;
            // display fully qualified parameter name - equals user's chosen parameter name if unique
            var parameterDesc = new JLabel(portDesc.getParameterName());
            resultPanel.add(parameterDesc, gbc);

            gbc.gridx = 1;
            resultPanel.add(new JLabel(" assign to "), gbc);

            gbc.gridx = parameterLabelFirst ? 2 : 0;
            JComboBox<String> comboBox = new JComboBox<>(portLabels);
            comboBox.setSelectedIndex(preSelectIndex);
            comboBox.setEnabled(false);
            resultPanel.add(comboBox, gbc);

            gbc.gridy++;
            preSelectIndex++;
        }
        return resultPanel;
    }

}