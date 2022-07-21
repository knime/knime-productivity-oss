/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
package org.knime.workflowservices.json.row.caller2;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
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
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.ColumnSelectionPanel;
import org.knime.json.util.JSONUtil;

/**
 * A panel for a single workflow parameter that expects JSON input.
 *
 * It allows the user to switch between using a hard-coded JSON value, the column driven value or the default.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class JSONInputPanel extends JPanel {

    private final String m_parameterIDSimple;

    private final JRadioButton m_useColumnChecker;

    private final JRadioButton m_useEditorChecker;

    private final JRadioButton m_useDefaultChecker;

    private final RSyntaxTextArea m_textEditArea;

    private final ColumnSelectionPanel m_jsonColumnSelector = new ColumnSelectionPanel((Border)null, JSONValue.class);

    private final JPanel m_hostPanel = new JPanel(new GridBagLayout());

    /**
     *
     * @param parameterName identifies the callee workflow input parameter
     * @param currentJSONValueOrNull optional default value to initialize the JSON editor with
     * @param spec provides the choices for the JSON column selector
     */
    public JSONInputPanel(final String parameterName, final JsonValue currentJSONValueOrNull,
        final DataTableSpec spec) {
        setName(parameterName);
        m_parameterIDSimple = CheckUtils.checkArgumentNotNull(parameterName);

        m_textEditArea = new RSyntaxTextArea(
            currentJSONValueOrNull == null ? "" : JSONUtil.toPrettyJSONString(currentJSONValueOrNull));
        m_textEditArea.setColumns(20);
        m_textEditArea.setRows(4);
        m_textEditArea.setAntiAliasingEnabled(true);
        m_textEditArea.setCodeFoldingEnabled(true);
        m_textEditArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        m_jsonColumnSelector.setRequired(false);

        m_useEditorChecker = new JRadioButton("Use custom JSON");
        m_useColumnChecker = new JRadioButton("From column");
        m_useDefaultChecker = new JRadioButton("Use default");
        var b = new ButtonGroup();
        b.add(m_useEditorChecker);
        b.add(m_useColumnChecker);
        b.add(m_useDefaultChecker);

        try {
            m_jsonColumnSelector.update(spec, "");
        } catch (NotConfigurableException e1) {
        }
        if (m_jsonColumnSelector.getNrItemsInList() == 0) {
            m_useColumnChecker.setEnabled(false);
            m_useColumnChecker.setToolTipText("Input table has no JSON columns");
        }

        final ActionListener l = e -> onButtonClick();
        m_useColumnChecker.addActionListener(l);
        m_useEditorChecker.addActionListener(l);
        m_useDefaultChecker.addActionListener(l);
        m_useDefaultChecker.doClick();
        createLayout();
    }

    /**
     * Populate the JSON column selectors with all JSON columns of the given spec.
     *
     * @param spec to look for JSON columns in.
     */
    void setInputTable(final DataTableSpec spec) {
        var selColName = m_jsonColumnSelector.getSelectedColumn();
        try {
            m_jsonColumnSelector.update(spec, selColName, false, true);
            m_jsonColumnSelector.setEnabled(true);
            m_useColumnChecker.setEnabled(true);
        } catch (NotConfigurableException e) {
            // spec doesn't contain a single JSON column
            m_jsonColumnSelector.setEnabled(false);
            m_useColumnChecker.setEnabled(false);
        }
    }

    /**
     * @param jsonValue a constant json value as data source.
     */
    void setJson(final JsonValue jsonValue) {
        m_textEditArea.setText(JSONUtil.toPrettyJSONString(jsonValue));
        m_useEditorChecker.doClick();
    }

    /**
     * @param columnName use JSON column with this name as data source.
     */
    void setColumnName(final String columnName) {
        m_useColumnChecker.doClick();
        m_jsonColumnSelector.setSelectedColumn(columnName);
    }

    /**
     * Set nothing as data source.
     */
    void setNoDataSource() {
        m_useDefaultChecker.doClick();
    }

    /**
     * @return the parameterIDSimple
     */
    String getParameterIDSimple() {
        return m_parameterIDSimple;
    }

    Optional<String> getJSONColumn() throws InvalidSettingsException {
        if (m_useColumnChecker.isSelected()) {
            // TODO this can be stale (combo box is still populated with JSON columns from an earlier configuration step)
            // TODO disable this when no JSON columns are present
            final var selectedColumn = m_jsonColumnSelector.getSelectedColumn();
            if (selectedColumn == null) {
                throw new InvalidSettingsException(
                    String.format("No JSON column available to send data to %s.", getParameterIDSimple()));
            }
            return Optional.of(selectedColumn);
        }
        return Optional.empty();
    }

    Optional<String> getJSONConfig() {
        if (m_useEditorChecker.isSelected()) {
            return Optional.of(m_textEditArea.getText());
        }
        return Optional.empty();
    }

    private void createLayout() {
        super.setLayout(new GridBagLayout());
        var gbc = new GridBagConstraints();
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
        var gbc = new GridBagConstraints();
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

}