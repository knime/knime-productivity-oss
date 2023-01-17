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
 *   Created on Feb 16, 2015 by wiswedel
 */
package org.knime.workflowservices.json.row.caller3;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.json.util.JSONUtil;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 * Stores the column names or JSON objects that provide the values for the input parameters of a callee workflow.
 *
 * Maintaining constant inputs in {@link #getParameterToJsonConfigMap()} and variable inputs in
 * {@link #getParameterToJsonColumnMap()} is useful because the constant inputs need to be sent only once to the callee,
 * while the variable inputs are send for every row.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public class CallWorkflowRowBased3Configuration extends CallWorkflowConnectionConfiguration  {

    /** @see #getParameterToJsonConfigMap() */
    private Map<String, ExternalNodeData> m_parameterToJsonConfigMap = Collections.emptyMap();

    /** @see #getParameterToJsonColumnMap() */
    private Map<String, String> m_parameterToJsonColumnMap = Collections.emptyMap();

    /** @see #isDropParameterIdentifiers() */
    private boolean m_dropParameterIdentifiers = false;

    /**
     * @param creationConfig
     */
    public CallWorkflowRowBased3Configuration(final NodeCreationConfiguration creationConfig) {
        super(creationConfig, CallWorkflowRowBased3NodeFactory.CONNECTION_INPUT_PORT_GRP_NAME);
    }

    // save & load

    /**
     * @param settings to save this configuration into
     */
    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        super.saveSettings(settings);

        // constant JSON inputs
        var staticInputs = settings.addNodeSettings("parameterToJsonConfigMap");
        for (Map.Entry<String, ExternalNodeData> entry : m_parameterToJsonConfigMap.entrySet()) {
            String parameterId = entry.getKey();
            String parameterName =
                isDropParameterIdentifiers() ? ExternalNodeData.getSimpleIDFrom(parameterId) : parameterId;
            var childSettings = staticInputs.addNodeSettings(parameterName);
            var json = entry.getValue().getJSONValue().toString(); //NOSONAR if we put an entry, it has a JSON value
            childSettings.addString("json", json);
        }

        // column inputs
        var dynamicInputs = settings.addNodeSettings("parameterToJsonColumnMap");
        for (Map.Entry<String, String> entry : m_parameterToJsonColumnMap.entrySet()) {
            String parameterId = entry.getKey();
            String parameterName =
                isDropParameterIdentifiers() ? ExternalNodeData.getSimpleIDFrom(parameterId) : parameterId;
            var childSettings = dynamicInputs.addNodeSettings(parameterName);
            childSettings.addString("json-column", entry.getValue());
        }

        // drop parameter identifiers
        settings.addBoolean("dropParameterIdentifiers", m_dropParameterIdentifiers);
    }

    /**
     * Called in the node model to read the information entered in the node dialog. This will throw exceptions if
     * something is wrong with the configuration.
     *
     * @param settings settings produced by the node dialog
     * @throws InvalidSettingsException
     */
    @Override
    public void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        super.loadSettingsInModel(settings);
        loadJsonConfigMap(settings, true);
        loadJsonColumnMap(settings, true);
        m_dropParameterIdentifiers = settings.getBoolean("dropParameterIdentifiers", false);
    }

    /**
     * Called in the node dialog to read persisted configuration. This will use default values if something is wrong
     * with the configuration.
     *
     * @param settings settings as persisted, e.g., to disk
     * @throws NotConfigurableException
     */
    @Override
    public void loadSettingsInDialog(final NodeSettingsRO settings) throws NotConfigurableException {
        super.loadSettingsInDialog(settings);

        try {
            loadJsonConfigMap(settings, false);
        } catch (InvalidSettingsException e) { // NOSONAR doesn't happen when strict = false
        }

        try {
            loadJsonColumnMap(settings, false);
        } catch (InvalidSettingsException e) { // NOSONAR doesn't happen when strict = false
        }
        m_dropParameterIdentifiers = settings.getBoolean("dropParameterIdentifiers", false);
    }

    /**
     * Loads the {@link #getParameterToJsonConfigMap()}
     *
     * @param settings to load from
     * @param strict
     * @throws InvalidSettingsException
     * @throws IOException if the JSON cannot be parsed
     */
    private void loadJsonConfigMap(final NodeSettingsRO settings, final boolean strict)
        throws InvalidSettingsException {
        var settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        for (String s : settings2.keySet()) {
            NodeSettingsRO childSettings;
            try {
                childSettings = settings2.getNodeSettings(s);
            } catch (InvalidSettingsException e) {
                if (strict) {
                    throw e;
                }
                continue;
            }
            var json = childSettings.getString("json");
            try {
                var object = JSONUtil.parseJSONValue(json);
                var parameterName = isDropParameterIdentifiers() ? ExternalNodeData.getSimpleIDFrom(s) : s;
                m_parameterToJsonConfigMap.put(parameterName,
                    ExternalNodeData.builder(parameterName).jsonValue(object).build());
            } catch (IOException ex) {
                if (strict) {
                    throw new InvalidSettingsException(
                        "Cannot parse the configured JSON value to pass to workflow input parameter " + s, ex);
                }
                throw new InvalidSettingsException("Invalid JSON string: " + ex.getMessage());
            }
        }
    }

    /**
     * Loads the {@link #getParameterToJsonColumnMap()}.
     *
     * @param settings to load from
     * @param strict whether to throw exceptions ({@link #loadInModel(NodeSettingsRO)}) or swallow them silently
     *            ({@link #loadInDialog(NodeSettingsRO)})
     * @throws InvalidSettingsException node settings do not contain a column map, or an entry in the column map does
     *             not contain a column name.
     */
    private void loadJsonColumnMap(final NodeSettingsRO settings, final boolean strict)
        throws InvalidSettingsException {
        m_parameterToJsonColumnMap = new LinkedHashMap<>();
        NodeSettingsRO settings3;
        try {
            settings3 = settings.getNodeSettings("parameterToJsonColumnMap");
        } catch (InvalidSettingsException e) {
            if (strict) {
                throw e;
            }
            return;
        }
        for (String s : settings3.keySet()) {
            try {
                var childSettings = settings3.getNodeSettings(s);
                var jsonColumn = childSettings.getString("json-column");
                var parameterName = isDropParameterIdentifiers() ? ExternalNodeData.getSimpleIDFrom(s) : s;
                m_parameterToJsonColumnMap.put(parameterName, jsonColumn);
            } catch (InvalidSettingsException e) {
                if (strict) {
                    throw e;
                }
            }
        }
    }

    /**
     * @param inputSpec table columns specification
     * @return maps a workflow input parameter name to the offset of the column in the given table spec with the same
     *         name
     */
    public Map<String, Integer> getParameterToJsonColumnIndexMap(final DataTableSpec inputSpec) {
        return getParameterToJsonColumnMap().entrySet().stream()//
            .map(e -> Map.entry(e.getKey(), inputSpec.findColumnIndex(e.getValue())))//
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // getters and setters

    /**
     * @return If the user manually enters a JSON object as source for a callee workflow input parameter, this maps the
     *         parameter name to the data to send.
     */
    public Map<String, ExternalNodeData> getParameterToJsonConfigMap() {
        return m_parameterToJsonConfigMap;
    }

    /**
     * @param map the parameterToJsonConfigMap to set
     * @return this object for fluent API
     */
    public CallWorkflowConnectionConfiguration setParameterToJsonConfigMap(final Map<String, ExternalNodeData> map) {
        CheckUtils.checkArgumentNotNull(map, "must not be null");
        m_parameterToJsonConfigMap = map;
        return this;
    }

    /**
     * @return If the user selects a column as source for a callee workflow input parameter, this maps the parameter
     *         name to the name of the column.
     */
    public Map<String, String> getParameterToJsonColumnMap() {
        return m_parameterToJsonColumnMap;
    }

    /**
     * @param map the parameterToJsonColumnMap to set
     * @return this object for fluent API
     */
    public CallWorkflowConnectionConfiguration setParameterToJsonColumnMap(final Map<String, String> map) {
        m_parameterToJsonColumnMap = map;
        return this;
    }

    /**
     * @return Whether to change the keys of the input data map sent to the callee workflow. If true, node IDs will be
     *         removed from parameter names (e.g., string-input instead of string-input-6).
     *
     *         This is added only for backward compatibility and is not exposed via the node dialog, just configurable
     *         via the flow variable tab.
     */
    public boolean isDropParameterIdentifiers() {
        return m_dropParameterIdentifiers;
    }

}
