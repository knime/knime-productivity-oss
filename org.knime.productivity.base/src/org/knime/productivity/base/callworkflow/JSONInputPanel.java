/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on 21.07.2015 by thor
 */
package org.knime.productivity.base.callworkflow;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.json.JsonValue;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.Border;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.ColumnSelectionPanel;
import org.knime.json.util.JSONUtil;

/**
 * A panel that given a json parameter
 *
 * @author wiswedel
 */
public final class JSONInputPanel extends JPanel {
    private final JRadioButton m_useColumnChecker;
    private final JRadioButton m_useEditorChecker;
    private final JRadioButton m_useDefaultChecker;
    private final RSyntaxTextArea m_textEditArea;
    private final ColumnSelectionPanel m_jsonColumnSelector;
    private final JPanel m_hostPanel;

    public JSONInputPanel(final JsonValue currentJSONValueOrNull, final DataTableSpec spec) {
        m_hostPanel = new JPanel(new GridBagLayout());

        m_textEditArea = new RSyntaxTextArea(currentJSONValueOrNull == null
                ? "" : JSONUtil.toPrettyJSONString(currentJSONValueOrNull));
        m_textEditArea.setColumns(20);
        m_textEditArea.setRows(4);
        m_textEditArea.setAntiAliasingEnabled(true);
        m_textEditArea.setCodeFoldingEnabled(true);
        m_textEditArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        m_jsonColumnSelector = new ColumnSelectionPanel((Border)null, JSONValue.class);
        m_jsonColumnSelector.setRequired(false);
        final ActionListener l = e -> onButtonClick();
        try {
            m_jsonColumnSelector.update(spec, "");
        } catch (NotConfigurableException e1) {
        }
        m_useEditorChecker = new JRadioButton("Use custom JSON");
        m_useColumnChecker = new JRadioButton("From column");
        m_useDefaultChecker = new JRadioButton("Use default");
        ButtonGroup b = new ButtonGroup();
        b.add(m_useEditorChecker);
        b.add(m_useColumnChecker);
        b.add(m_useDefaultChecker);
        m_useColumnChecker.addActionListener(l);
        m_useEditorChecker.addActionListener(l);
        m_useDefaultChecker.addActionListener(l);
        m_useDefaultChecker.doClick();
        createLayout();
    }

    void update(final DataTableSpec spec, final Parameter parameter) {
        final Optional<JsonValue> jsonOptional = parameter.getJsonValue();
        final Optional<String> jsonColumnOptional = parameter.getJsonColumn();
        try {
            m_jsonColumnSelector.update(spec, jsonColumnOptional.orElse(null), false, true);
        } catch (NotConfigurableException e) {
        }
        if (jsonOptional.isPresent()) {
            m_textEditArea.setText(JSONUtil.toPrettyJSONString(jsonOptional.get()));
        }
        if (jsonColumnOptional.isPresent()) {
            m_useColumnChecker.doClick();
        } else if (jsonOptional.isPresent()) {
            m_useEditorChecker.doClick();
        } else {
            m_useDefaultChecker.doClick();
        }
    }

    private Optional<String> getJSONColumn() {
        if (m_useColumnChecker.isSelected()) {
            return Optional.of(m_jsonColumnSelector.getSelectedColumn());
        }
        return Optional.empty();
    }

    private Optional<String> getJSONConfig() {
        if (m_useEditorChecker.isSelected()) {
            return Optional.of(m_textEditArea.getText());
        }
        return Optional.empty();
    }

    private void createLayout() {
        super.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        add(m_useColumnChecker, gbc);

        gbc.insets = new Insets(5, 15, 5, 5);
        gbc.gridx += 1;
        add(m_useEditorChecker, gbc);

        gbc.gridx += 1;
        add(m_useDefaultChecker, gbc);

        gbc.gridx = 0;
        gbc.gridy += 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1;
        gbc.weighty = 1;
        add(m_hostPanel, gbc);
    }

    private void onButtonClick() {
        m_hostPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.insets = new Insets(0, 20, 0, 0);

        if (m_useEditorChecker.isSelected()) {
            gbc.fill = GridBagConstraints.BOTH;
            m_hostPanel.add(new RTextScrollPane(m_textEditArea, true), gbc);
        } else if (m_useColumnChecker.isSelected()) {
            gbc.fill = GridBagConstraints.NONE;
            m_hostPanel.add(m_jsonColumnSelector, gbc);
        } else {
            gbc.fill = GridBagConstraints.NONE;
            m_hostPanel.add(new JLabel(), gbc);
        }
        invalidate();
        revalidate();
        repaint();
    }

    /** Called by the individual nodes to save the configs in a list of panels into the final configuration.
     * @param config To save to.
     * @param panels The list (map) of panels
     * @throws InvalidSettingsException On invalid format.
     */
    public static void saveSettingsTo(final CallWorkflowConfiguration config,
        final Iterable<Map.Entry<String, JSONInputPanel>> panels) throws InvalidSettingsException {
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, ExternalNodeData> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (Map.Entry<String, JSONInputPanel> entry : panels) {
            final String key = entry.getKey();
            final JSONInputPanel p = entry.getValue();
            final Optional<String> jsonColumn = p.getJSONColumn();
            final Optional<String> jsonConfig = p.getJSONConfig();
            if (jsonColumn.isPresent()) {
                parameterToJsonColumnMap.put(key, jsonColumn.get());
            } else if (jsonConfig.isPresent()) {
                try {
                    parameterToJsonConfigMap.put(key,
                        ExternalNodeData.builder(key).jsonValue(JSONUtil.parseJSONValue(jsonConfig.get())).build());
                } catch (IOException ex) {
                    throw new InvalidSettingsException("No valid JSON for key " + key + ": " + ex.getMessage(), ex);
                }
            } else {
                // do nothing and do not mention this column in the settings -- it's using 'default' then
            }
        }
        config.setParameterToJsonColumnMap(parameterToJsonColumnMap);
        config.setParameterToJsonConfigMap(parameterToJsonConfigMap);
    }

    /**
     * Counterpart to {@link #saveSettingsTo(CallWorkflowConfiguration, Iterable)}.
     * @param config To load from.
     * @param panelMap The map of panels to load into.
     * @param spec The data spec (for column list)
     */
    public static void loadSettingsFrom(final CallWorkflowConfiguration config,
        final Map<String, JSONInputPanel> panelMap, final DataTableSpec spec) {

        Map<String, Parameter> parameterMapFromConfig = new LinkedHashMap<>();

        for (Map.Entry<String, ExternalNodeData> entry : config.getParameterToJsonConfigMap().entrySet()) {
            parameterMapFromConfig.put(entry.getKey(), new Parameter(entry.getValue().getJSONValue()));
        }

        for (Map.Entry<String, String> entry : config.getParameterToJsonColumnMap().entrySet()) {
            parameterMapFromConfig.put(entry.getKey(), new Parameter(entry.getValue()));
        }

        for (Map.Entry<String, JSONInputPanel> entry : panelMap.entrySet()) {
            Parameter parameter = parameterMapFromConfig.getOrDefault(entry.getKey(), Parameter.DEFAULT);
            entry.getValue().update(spec, parameter);
        }
    }

    /** Helper to remember what sort of mapping we have for a parameter: column driven, static
     * or 'nothing' (use default value assigned in the called workflow).
     */
    private static final class Parameter {
        private final String m_jsonColumn;
        private final JsonValue m_jsonValue;

        static final Parameter DEFAULT = new Parameter((String)null);

        Parameter(final String jsonColumn) {
            m_jsonColumn = jsonColumn;
            m_jsonValue = null;
        }

        Parameter(final JsonValue jsonValue) {
            m_jsonValue = jsonValue;
            m_jsonColumn = null;
        }

        Optional<String> getJsonColumn() {
            return Optional.ofNullable(m_jsonColumn);
        }
        Optional<JsonValue> getJsonValue() {
            return Optional.ofNullable(m_jsonValue);
        }

        @Override
        public String toString() {
            if (m_jsonColumn != null) {
                return "JSON Column: " + m_jsonColumn;
            } else if (m_jsonValue != null) {
                return "Static value: " + m_jsonValue.toString();
            } else {
                return "Default";
            }
        }

    }

}