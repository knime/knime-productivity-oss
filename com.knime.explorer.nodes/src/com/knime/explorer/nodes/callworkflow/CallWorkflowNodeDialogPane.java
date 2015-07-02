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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.ColumnSelectionPanel;

import com.knime.explorer.nodes.callworkflow.RemoteWorkflowBackend.Lookup;

/**
 * Dialog to node.
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class CallWorkflowNodeDialogPane extends NodeDialogPane {

    private final JTextField m_hostField;
    private final JTextField m_usernameField;
    private final JPasswordField m_passwordField;
    private final JTextField m_workflowPathField;
    private final JLabel m_errorLabel;
    private final VerticalCollapsablePanels m_collapsablePanels;
    private final Map<String, MyPanel> m_panelMap;

    private DataTableSpec m_spec;
    private final boolean m_isRemote;

    CallWorkflowNodeDialogPane(final boolean isRemote) {
        final JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_isRemote = isRemote;
        m_hostField = new JTextField();
        m_usernameField = new JTextField();
        m_passwordField = new JPasswordField();
        m_workflowPathField = new JTextField();
        m_workflowPathField.addKeyListener(new KeyAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void keyTyped(final KeyEvent e) {
                m_collapsablePanels.removeAll();
                m_panelMap.clear();
                m_errorLabel.setText(" ");
                p.revalidate();
            }
        });

        m_errorLabel = new JLabel();
        m_errorLabel.setForeground(Color.RED.darker());
        m_collapsablePanels = new VerticalCollapsablePanels(true, 0);
        m_panelMap = new LinkedHashMap<>();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.weightx = 1.0;
        gbc.gridx = gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        if (m_isRemote) {
            gbc.weightx = 0.0;
            p.add(new JLabel("Host: "), gbc);
            gbc.gridx += 1;
            gbc.weightx = 1.0;
            gbc.gridwidth = 2;
            p.add(m_hostField, gbc);
            gbc.gridx = 0;
            gbc.gridy += 1;

            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            p.add(new JLabel("User: "), gbc);
            gbc.gridx += 1;
            gbc.weightx = 1.0;
            gbc.gridwidth = 2;
            p.add(m_usernameField, gbc);
            gbc.gridx = 0;
            gbc.gridy += 1;

            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            p.add(new JLabel("Password: "), gbc);
            gbc.gridx += 1;
            gbc.weightx = 1.0;
            gbc.gridwidth = 2;
            p.add(m_passwordField, gbc);
            gbc.gridx = 0;
            gbc.gridy += 1;
        }

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

        if (!m_isRemote) {
            gbc.gridy += 1;
            p.add(new JLabel("(can use \"knime://knime.workflow/..\"): "), gbc);
        }

        gbc.gridwidth = 3;
        gbc.gridx = 0;
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
        CallWorkflowConfiguration c = new CallWorkflowConfiguration(m_isRemote);
        c.setWorkflowPath(m_workflowPathField.getText());
        if (m_isRemote) {
            c.setRemoteHostAndPort(m_hostField.getText());
            c.setUsername(m_usernameField.getText());
            c.setPassword(new String(m_passwordField.getPassword()));
        }
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, ExternalNodeData> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (Map.Entry<String, MyPanel> entry : m_panelMap.entrySet()) {
            final String key = entry.getKey();
            final MyPanel p = entry.getValue();
            final String jsonColumn = p.getJSONColumn();
            if (jsonColumn != null) {
                parameterToJsonColumnMap.put(key, jsonColumn);
            } else {
                parameterToJsonConfigMap.put(key,
                    ExternalNodeData.builder(key).jsonObject(toJSONObject(key, p.getJSONConfig())).build());
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
        CallWorkflowConfiguration c = new CallWorkflowConfiguration(m_isRemote);
        c.loadInDialog(settings);
        m_hostField.setText(c.getRemoteHostAndPort());
        m_usernameField.setText(c.getUsername());
        m_passwordField.setText(c.getPassword());
        m_spec = specs[0];

        m_workflowPathField.setText(c.getWorkflowPath());
        updatePanels();

        for (Map.Entry<String, ExternalNodeData> entry : c.getParameterToJsonConfigMap().entrySet()) {
            MyPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(m_spec, entry.getValue().getJSONObject(), null);
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
        if (m_workflowPathField.getText().isEmpty()) {
            m_errorLabel.setText("No workflow path provided");
        } else {
            try (IWorkflowBackend backend = newBackend()) {
                Map<String, ExternalNodeData> inputNodes = backend.getInputNodes();
                for (Map.Entry<String, ExternalNodeData> entry : inputNodes.entrySet()) {
                    MyPanel p = new MyPanel(entry.getValue().getJSONObject(), m_spec);
                    m_panelMap.put(entry.getKey(), p);
                    m_collapsablePanels.addPanel(p, false, entry.getKey());
                }
            } catch (Exception e) {
                NodeLogger.getLogger(getClass()).debug(e.getMessage(), e);
                m_errorLabel.setText("<html><body>" + e.getMessage() + "</body></html>");
                return;
            }
        }
    }

    private IWorkflowBackend newBackend() throws Exception {
        final String workflowPath = m_workflowPathField.getText();
        if (m_isRemote) {
            final String hostAndPort = m_hostField.getText();
            String username = m_usernameField.getText();
            String password = new String(m_passwordField.getPassword());
            return RemoteWorkflowBackend.newInstance(Lookup.newLookup(hostAndPort, workflowPath, username, password));
        } else {
            return LocalWorkflowBackend.get(workflowPath);
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
