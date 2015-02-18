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
package com.knime.explorer.nodes.callworkflow;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.Border;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.base.util.ui.VerticalCollapsablePanels;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.ColumnSelectionPanel;

/**
 *
 * @author wiswedel
 */
final class CallWorkflowNodeDialogPane extends NodeDialogPane {

    private final JTextField m_workflowPath;
    private final JLabel m_errorLabel;
    private final VerticalCollapsablePanels m_collapsablePanels;
    private final Map<String, MyPanel> m_panelMap;

    private DataTableSpec m_spec;

    CallWorkflowNodeDialogPane() {
        m_workflowPath = new JTextField();
        m_workflowPath.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                updatePanels();
            }
        });
        m_errorLabel = new JLabel();
        m_errorLabel.setForeground(Color.RED.darker());
        m_collapsablePanels = new VerticalCollapsablePanels(true, 0);
        m_panelMap = new LinkedHashMap<>();
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.gridx = gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        p.add(new JLabel("Workflow URI (can use \"knime://knime.workflow/..\"): "), gbc);
        gbc.gridy += 1;
        p.add(m_workflowPath, gbc);
        gbc.gridy += 1;
        p.add(m_errorLabel, gbc);
        gbc.gridy += 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        p.add(m_collapsablePanels, gbc);
        addTab("Workflow", p);
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isBlank(m_errorLabel.getText()) , "No valid workflow selected");
        CallWorkflowConfiguration c = new CallWorkflowConfiguration();
        c.setWorkflowPath(m_workflowPath.getText());
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, JsonObject> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (Map.Entry<String, MyPanel> entry : m_panelMap.entrySet()) {
            final String key = entry.getKey();
            final MyPanel p = entry.getValue();
            final String jsonColumn = p.getJSONColumn();
            if (jsonColumn != null) {
                parameterToJsonColumnMap.put(key, jsonColumn);
            } else {
                parameterToJsonConfigMap.put(key, toJSONObject(key, p.getJSONConfig()));
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
        CallWorkflowConfiguration c = new CallWorkflowConfiguration();
        c.loadInDialog(settings);
        m_spec = specs[0];

        m_workflowPath.setText(c.getWorkflowPath());
        updatePanels();

        for (Map.Entry<String, JsonObject> entry : c.getParameterToJsonConfigMap().entrySet()) {
            MyPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(m_spec, entry.getValue(), null);
            }
        }

        for (Map.Entry<String, String> entry : c.getParameterToJsonColumnMap().entrySet()) {
            MyPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(specs[0], null, entry.getValue());
            }
        }
    }

    private void updatePanels() {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");

        try (LocalWorkflowBackend localWF = LocalWorkflowBackend.get(m_workflowPath.getText())) {
            Map<String, JsonObject> inputNodes = localWF.getInputNodes();
            for (Map.Entry<String, JsonObject> entry : inputNodes.entrySet()) {
                MyPanel p = new MyPanel(entry.getValue(), m_spec);
                m_panelMap.put(entry.getKey(), p);
                m_collapsablePanels.addPanel(p, false, entry.getKey());
            }
        } catch (Exception e) {
            m_errorLabel.setText("<html><body>" + e.getMessage() + "</body></html>");
            return;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        m_spec = null;
    }

    private static String toPrettyString(final JsonObject object) {
        StringWriter w = new StringWriter();
        final Map<String, Boolean> cfg = Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        try (JsonWriter writer = Json.createWriterFactory(cfg).createWriter(w)) {
            writer.writeObject(object);
        }
        return w.toString();
    }

    private static JsonObject toJSONObject(final String key, final String str) throws InvalidSettingsException {
        String nonEmptyString = StringUtils.defaultIfBlank(str, "{}");
        try (JsonReader r = Json.createReader(new StringReader(nonEmptyString))) {
            return r.readObject();
        } catch (Exception e) {
            throw new InvalidSettingsException("No valid JSON for key " + key, e);
        }
    }

    private static final class MyPanel extends JPanel {

        private final JRadioButton m_useColumnChecker;
        private final JRadioButton m_useEditorChecker;
        private final RSyntaxTextArea m_textEditArea;
        private final ColumnSelectionPanel m_jsonColumnSelector;
        private final JPanel m_hostPanel;

        MyPanel(final JsonObject currentJSONValueOrNull, final DataTableSpec spec) {
            m_hostPanel = new JPanel();
            m_textEditArea = new RSyntaxTextArea(currentJSONValueOrNull == null
                    ? "" : toPrettyString(currentJSONValueOrNull));
            m_textEditArea.setColumns(80);
            m_textEditArea.setRows(15);
            m_textEditArea.setAntiAliasingEnabled(true);
            m_textEditArea.setCodeFoldingEnabled(true);
            m_textEditArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
            m_jsonColumnSelector = new ColumnSelectionPanel((Border)null, JSONValue.class);
            m_jsonColumnSelector.setRequired(false);
            final ActionListener l = new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    onButtonClick();
                }
            };
            try {
                m_jsonColumnSelector.update(spec, "");
            } catch (NotConfigurableException e1) {
            }
            m_useEditorChecker = new JRadioButton("Use custom JSON");
            m_useColumnChecker = new JRadioButton("From column");
            ButtonGroup b = new ButtonGroup();
            b.add(m_useEditorChecker);
            b.add(m_useColumnChecker);
            m_useColumnChecker.addActionListener(l);
            m_useEditorChecker.addActionListener(l);
            m_useEditorChecker.doClick();
            createLayout();
        }

        private void update(final DataTableSpec spec, final JsonObject jsonOrNull, final String jsonColumnOrNull) {
            try {
                m_jsonColumnSelector.update(spec, jsonColumnOrNull, false, true);
            } catch (NotConfigurableException e) {
            }
            if (jsonOrNull != null) {
                m_textEditArea.setText(toPrettyString(jsonOrNull));
            }
            if (jsonColumnOrNull != null) {
                m_useColumnChecker.doClick();
            } else {
                m_useEditorChecker.doClick();
            }
        }

        String getJSONColumn() {
            if (m_useColumnChecker.isSelected()) {
                return m_jsonColumnSelector.getSelectedColumn();
            }
            return null;
        }

        String getJSONConfig() {
            return m_textEditArea.getText();
        }

        private void createLayout() {
            super.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            add(m_useColumnChecker, gbc);
            gbc.gridx += 1;
            add(m_useEditorChecker, gbc);
            gbc.gridx = 0;
            gbc.gridy += 1;
            gbc.gridwidth = 2;
            add(m_hostPanel, gbc);
        }

        private void onButtonClick() {
            m_hostPanel.removeAll();
            m_hostPanel.add(m_useEditorChecker.isSelected()
                ? new RTextScrollPane(m_textEditArea, true) : m_jsonColumnSelector);
            invalidate();
            revalidate();
            repaint();
        }

    }

}
