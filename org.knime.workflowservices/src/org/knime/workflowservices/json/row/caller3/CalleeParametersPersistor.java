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
 *   Created on Jan 9, 2026 by paulbaernreuther
 */
package org.knime.workflowservices.json.row.caller3;

import static org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3Configuration.DROP_PARAMETER_IDENTIFIERS_CFG_KEY;
import static org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3Configuration.JSON_CFG_KEY;
import static org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3Configuration.JSON_COLUMN_CFG_KEY;
import static org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3Configuration.PARAMETER_TO_JSON_COLUMN_MAP_CFG_KEY;
import static org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3Configuration.PARAMETER_TO_JSON_CONFIG_MAP_CFG_KEY;

import java.util.ArrayList;
import java.util.List;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.node.parameters.migration.ConfigMigration;
import org.knime.node.parameters.migration.NodeParametersMigration;
import org.knime.node.parameters.persistence.NodeParametersPersistor;

/**
 * Custom persistor that converts between the old two-map format and the new ContainerInputParameters[] array.
 */
final class CalleeParametersPersistor implements NodeParametersPersistor<CalleeParameters> {

    @Override
    public CalleeParameters load(final NodeSettingsRO settings) throws InvalidSettingsException {
        final var params = new CalleeParameters();
        final var parametersList = new ArrayList<ContainerInputParameters>();

        params.m_dropParameterIdentifiers = settings.getBoolean(DROP_PARAMETER_IDENTIFIERS_CFG_KEY, false);

        var customInputs = settings.getNodeSettings(PARAMETER_TO_JSON_CONFIG_MAP_CFG_KEY);
        for (String s : customInputs.keySet()) {
            try {
                final var childSettings = customInputs.getNodeSettings(s);
                var json = childSettings.getString(JSON_CFG_KEY);
                var parameterName = params.m_dropParameterIdentifiers ? ExternalNodeData.getSimpleIDFrom(s) : s;
                var containerParam = new ContainerInputParameters();
                containerParam.m_parameterName = parameterName;
                containerParam.m_inputOption = JsonInputOption.CUSTOM;
                containerParam.m_customJson = json;
                parametersList.add(containerParam);
            } catch (InvalidSettingsException e) { // NOSONAR Should not happen
                continue;
            }
        }
        var columnInputs = settings.getNodeSettings(PARAMETER_TO_JSON_COLUMN_MAP_CFG_KEY);
        for (String s : columnInputs.keySet()) {
            try {
                var childSettings = columnInputs.getNodeSettings(s);
                var jsonColumn = childSettings.getString(JSON_COLUMN_CFG_KEY);
                // Apply dropParameterIdentifiers transformation - copied from loadJsonColumnMap line 240
                var parameterName = params.m_dropParameterIdentifiers ? ExternalNodeData.getSimpleIDFrom(s) : s;
                var containerParam = new ContainerInputParameters();
                containerParam.m_parameterName = parameterName;
                containerParam.m_inputOption = JsonInputOption.COLUMN;
                containerParam.m_jsonColumn = jsonColumn;
                parametersList.add(containerParam);
            } catch (InvalidSettingsException e) { // NOSONAR Should not happen
                // Skip invalid entry
            }
        }
        params.m_containerInputParameters = parametersList.toArray(new ContainerInputParameters[0]);
        return params;
    }

    @Override
    public void save(final CalleeParameters param, final NodeSettingsWO settings) {
        var customInputs = settings.addNodeSettings(PARAMETER_TO_JSON_CONFIG_MAP_CFG_KEY);
        for (ContainerInputParameters containerParam : param.m_containerInputParameters) {
            if (containerParam.m_inputOption == JsonInputOption.CUSTOM) {
                String parameterId = containerParam.m_parameterName;
                String parameterName =
                    param.m_dropParameterIdentifiers ? ExternalNodeData.getSimpleIDFrom(parameterId) : parameterId;
                var childSettings = customInputs.addNodeSettings(parameterName);
                childSettings.addString(JSON_CFG_KEY, containerParam.m_customJson);
            }
        }

        var columnInputs = settings.addNodeSettings(PARAMETER_TO_JSON_COLUMN_MAP_CFG_KEY);
        for (ContainerInputParameters containerParam : param.m_containerInputParameters) {
            if (containerParam.m_inputOption == JsonInputOption.COLUMN) {
                String parameterId = containerParam.m_parameterName;
                String parameterName =
                    param.m_dropParameterIdentifiers ? ExternalNodeData.getSimpleIDFrom(parameterId) : parameterId;
                var childSettings = columnInputs.addNodeSettings(parameterName);
                childSettings.addString(JSON_COLUMN_CFG_KEY, containerParam.m_jsonColumn);
            }
        }

        settings.addBoolean(DROP_PARAMETER_IDENTIFIERS_CFG_KEY, param.m_dropParameterIdentifiers);
    }

    @Override
    public String[][] getConfigPaths() {
        return new String[0][];
    }

    /**
     * Since it is impossible with the current framework to make the legacy flow variable structure available in the
     * dialog, we at least have this dummy migration that leads to old flow variables being read-only visible in the
     * dialog, so that users are aware of their existence and can unset them.
     */
    static final class MigrationForShowingLegacyCfgKeys implements NodeParametersMigration<CalleeParameters> {

        @Override
        public List<ConfigMigration<CalleeParameters>> getConfigMigrations() {
            return List.of(ConfigMigration.<CalleeParameters> builder(settings -> null).withMatcher(settings -> false)
                .withDeprecatedConfigPath(PARAMETER_TO_JSON_CONFIG_MAP_CFG_KEY)//
                .withDeprecatedConfigPath(PARAMETER_TO_JSON_COLUMN_MAP_CFG_KEY)//
                .withDeprecatedConfigPath(DROP_PARAMETER_IDENTIFIERS_CFG_KEY)//
                .build());
        }

    }
}