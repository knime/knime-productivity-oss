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
 *   Created on 21.07.2015 by thor
 */
package com.knime.explorer.nodes.callworkflow;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.json.JsonObject;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.Border;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.util.ColumnSelectionPanel;
import org.knime.json.util.JSONUtil;

public final class JSONInputPanel extends JPanel {
    private final JRadioButton m_useColumnChecker;
    private final JRadioButton m_useEditorChecker;
    private final RSyntaxTextArea m_textEditArea;
    private final ColumnSelectionPanel m_jsonColumnSelector;
    private final JPanel m_hostPanel;

    public JSONInputPanel(final JsonObject currentJSONValueOrNull, final DataTableSpec spec) {
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

    public void update(final DataTableSpec spec, final JsonObject jsonOrNull, final String jsonColumnOrNull) {
        try {
            m_jsonColumnSelector.update(spec, jsonColumnOrNull, false, true);
        } catch (NotConfigurableException e) {
        }
        if (jsonOrNull != null) {
            m_textEditArea.setText(JSONUtil.toPrettyJSONString(jsonOrNull));
        }
        if (jsonColumnOrNull != null) {
            m_useColumnChecker.doClick();
        } else {
            m_useEditorChecker.doClick();
        }
    }

    public String getJSONColumn() {
        if (m_useColumnChecker.isSelected()) {
            return m_jsonColumnSelector.getSelectedColumn();
        }
        return null;
    }

    public String getJSONConfig() {
        return m_textEditArea.getText();
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

        gbc.gridx = 0;
        gbc.gridy += 1;
        gbc.gridwidth = 2;
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
        } else {
            gbc.fill = GridBagConstraints.NONE;
            m_hostPanel.add(m_jsonColumnSelector, gbc);

        }
        invalidate();
        revalidate();
        repaint();
    }

}