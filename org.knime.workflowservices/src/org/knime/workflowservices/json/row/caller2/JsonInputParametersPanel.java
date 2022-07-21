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
 *   Created on 12 Jul 2022 by carlwitt
 */
package org.knime.workflowservices.json.row.caller2;

import java.awt.CardLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.VerticalCollapsablePanels;
import org.knime.json.util.JSONUtil;

final class JsonInputParametersPanel {

    /** contains all controls */
    private final JPanel m_panel = new JPanel();

    private final VerticalCollapsablePanels m_collapsablePanels = new VerticalCollapsablePanels();

    private final Map<String, JSONInputPanel> m_panelMap = new LinkedHashMap<>();

    /** Shows either a the controls (state "OK") or an error message (state "ERROR"). */
    private final CardLayout m_cardLayout = new CardLayout();

    /** Displays an error message when fetching the workflow paths is not possible. */
    private final JLabel m_errorLabel = new JLabel();

    /**
     *
     */
    public JsonInputParametersPanel() {
        m_panel.setBorder(BorderFactory.createTitledBorder("Input Parameters"));

        m_panel.setLayout(m_cardLayout);

        m_panel.add(m_collapsablePanels, "OK");

        m_errorLabel.setForeground(Color.RED.darker());
        m_panel.add(m_errorLabel, "ERROR");

        m_cardLayout.show(m_panel, "OK");
    }

    /**
     * Delete and create all parameter input panels.
     *
     * Optionally call {@link #loadConfiguration(CallWorkflowRowBasedConfiguration)} afterwards to re-apply previous
     * selections if possible.
     *
     * @param inputNodes provides parameter names and their optional default JSON values
     * @param inputTableSpec provides the JSON columns that can be selected as data source
     */
    public void createPanels(final Map<String, ExternalNodeData> inputNodes, final DataTableSpec inputTableSpec) {

        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        final var sortedByParameterName =
            inputNodes.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).collect(Collectors.toList());
        for (var i = 0; i < sortedByParameterName.size(); i++) {
            var entry = sortedByParameterName.get(i);
            var p = new JSONInputPanel(entry.getKey(), entry.getValue().getJSONValue(), inputTableSpec);
            p.setSelectedIndex(i);
            m_panelMap.put(entry.getKey(), p);
            m_collapsablePanels.addPanel(p, false);
        }

        if (inputNodes.isEmpty()) {
            m_panel.setBorder(BorderFactory.createTitledBorder("Input Parameters (workflow has none)"));
        } else {
            m_panel.setBorder(BorderFactory.createTitledBorder("Input Parameters"));
        }
        m_panel.revalidate();
        m_panel.repaint();
    }

    public void clear() {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_collapsablePanels.revalidate();
    }

    public void setError(final String message) {
        m_errorLabel.setText(message);
        m_cardLayout.show(m_panel, "ERROR");
    }

    public void clearError() {
        m_errorLabel.setText("");
        m_cardLayout.show(m_panel, "OK");
    }

    /**
     * Called by the individual nodes to save the configs in a list of panels into the final configuration.
     *
     * @param configuration to save to.
     * @throws InvalidSettingsException On invalid format.
     */
    public void saveToConfiguration(final CallWorkflowRowBasedConfiguration configuration)
        throws InvalidSettingsException {
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, ExternalNodeData> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (JSONInputPanel panel : m_panelMap.values()) {
            final var key = panel.getParameterID();
            final var jsonColumn = panel.getJSONColumn();
            final var jsonConfig = panel.getJSONConfig();
            configuration.setDropParameterIdentifier(key, panel.isDropParameterIdentifier());
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
        configuration.setParameterToJsonColumnMap(parameterToJsonColumnMap);
        configuration.setParameterToJsonConfigMap(parameterToJsonConfigMap);
    }

    public void setInputTableSpec(final DataTableSpec inputTableSpec) {
        m_panelMap.values().stream().forEach(p -> p.setInputTable(inputTableSpec));
    }

    /**
     * @param configuration contains the data sources for the callee workflow input parameters (source column or fixed
     *            JSON object)
     */
    public void loadConfiguration(final CallWorkflowRowBasedConfiguration configuration) {

        var columnSource = configuration.getParameterToJsonColumnMap();
        var fixedSource = configuration.getParameterToJsonConfigMap();

        for (var panel : m_panelMap.values()) {
            final var parameterId = panel.getParameterID();
            if (columnSource.containsKey(parameterId)) {
                panel.setColumnName(columnSource.get(parameterId));
            } else if (fixedSource.containsKey(parameterId)) {
                panel.setJson(fixedSource.get(parameterId).getJSONValue());
            } else {
                panel.setNoDataSource();
            }
            if(configuration.isDropParameterIdentifiers(parameterId)) {
                panel.setDropParameterIdentifier();
            }
        }
    }

    /**
     * @return the panel that contains all controls
     */
    public JPanel getPanel() {
        return m_panel;
    }

}