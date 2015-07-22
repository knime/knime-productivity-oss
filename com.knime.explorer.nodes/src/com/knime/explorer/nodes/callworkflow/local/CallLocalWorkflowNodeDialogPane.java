/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 16, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow.local;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.JsonException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.VerticalCollapsablePanels;
import org.knime.json.util.JSONUtil;

import com.knime.explorer.nodes.callworkflow.IWorkflowBackend;
import com.knime.explorer.nodes.callworkflow.JSONInputPanel;

/**
 * Dialog to node.
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class CallLocalWorkflowNodeDialogPane extends NodeDialogPane {
    private final JTextField m_workflowPathField;
    private final JLabel m_errorLabel;
    private final VerticalCollapsablePanels m_collapsablePanels;
    private final Map<String, JSONInputPanel> m_panelMap;

    private DataTableSpec m_spec;

    CallLocalWorkflowNodeDialogPane() {
        final JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_workflowPathField = new JTextField(20);
        m_workflowPathField.addKeyListener(new KeyAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void keyTyped(final KeyEvent e) {
                m_collapsablePanels.removeAll();
                m_panelMap.clear();
                m_errorLabel.setText(" ");
                m_collapsablePanels.revalidate();
                p.revalidate();
                CallLocalWorkflowNodeDialogPane.this.getPanel().repaint();
            }
        });

        m_errorLabel = new JLabel();
        m_errorLabel.setForeground(Color.RED.darker());
        m_collapsablePanels = new VerticalCollapsablePanels();
        m_panelMap = new LinkedHashMap<>();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.weightx = 1.0;
        gbc.gridx = gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        p.add(new JLabel("Workflow Path: "), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        p.add(m_workflowPathField, gbc);

        JButton b = new JButton("Load input format");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                updatePanels();
            }
        });
        gbc.gridx++;
        gbc.weightx = 0;
        p.add(b, gbc);

        gbc.gridx = 1;
        gbc.gridy += 1;
        p.add(new JLabel("(can use \"knime://knime.workflow/..\"): "), gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy += 1;
        p.add(m_errorLabel, gbc);
        gbc.gridy += 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        p.add(m_collapsablePanels, gbc);

        p.setMinimumSize(new Dimension(600, 300));
        p.setPreferredSize(new Dimension(600, 300));
        addTab("Workflow", p);
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isBlank(m_errorLabel.getText()) , "No valid workflow selected");
        CallLocalWorkflowConfiguration c = new CallLocalWorkflowConfiguration();
        c.setWorkflowPath(m_workflowPathField.getText());
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, ExternalNodeData> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (Map.Entry<String, JSONInputPanel> entry : m_panelMap.entrySet()) {
            final String key = entry.getKey();
            final JSONInputPanel p = entry.getValue();
            final String jsonColumn = p.getJSONColumn();
            if (jsonColumn != null) {
                parameterToJsonColumnMap.put(key, jsonColumn);
            } else {
                try {
                    parameterToJsonConfigMap.put(key,
                        ExternalNodeData.builder(key).jsonObject(JSONUtil.parseJSONValue(p.getJSONConfig())).build());
                } catch (JsonException ex) {
                    throw new InvalidSettingsException("No valid JSON for key " + key + ": " + ex.getMessage(), ex);
                }
            }
        }
        c.setParameterToJsonColumnMap(parameterToJsonColumnMap);
        c.setParameterToJsonConfigMap(parameterToJsonConfigMap);
        c.save(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
            throws NotConfigurableException {
        CallLocalWorkflowConfiguration c = new CallLocalWorkflowConfiguration();
        c.loadInDialog(settings);
        m_spec = specs[0];

        m_workflowPathField.setText(c.getWorkflowPath());
        updatePanels();

        for (Map.Entry<String, ExternalNodeData> entry : c.getParameterToJsonConfigMap().entrySet()) {
            JSONInputPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(m_spec, entry.getValue().getJSONObject(), null);
            }
        }

        for (Map.Entry<String, String> entry : c.getParameterToJsonColumnMap().entrySet()) {
            JSONInputPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(specs[0], null, entry.getValue());
            }
        }
    }

    private void updatePanels() {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");
        if (m_workflowPathField.getText().isEmpty()) {
            m_errorLabel.setText("No workflow path provided");
        } else {
            try (IWorkflowBackend backend = newBackend()) {
                Map<String, ExternalNodeData> inputNodes = backend.getInputNodes();
                for (Map.Entry<String, ExternalNodeData> entry : inputNodes.entrySet()) {
                    JSONInputPanel p = new JSONInputPanel(entry.getValue().getJSONObject(), m_spec);
                    m_panelMap.put(entry.getKey(), p);
                    m_collapsablePanels.addPanel(p, false, entry.getKey());
                }
            } catch (Exception e) {
                NodeLogger.getLogger(getClass()).debug(e.getMessage(), e);
                m_errorLabel.setText("<html><body>" + e.getMessage() + "</body></html>");
                return;
            }
        }
        getPanel().revalidate();
    }

    private IWorkflowBackend newBackend() throws Exception {
        final String workflowPath = m_workflowPathField.getText();
        return LocalWorkflowBackend.newInstance(workflowPath, getNodeContext().getWorkflowManager().getContext());
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        m_spec = null;
    }
}
