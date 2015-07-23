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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.JsonException;
import javax.json.JsonObject;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.json.util.JSONUtil;

/**
 * Config object to node. Holds (remote) workflow URI and parameters.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
public abstract class CallWorkflowConfiguration {
    private Map<String, ExternalNodeData> m_parameterToJsonConfigMap = Collections.emptyMap();

    private Map<String, String> m_parameterToJsonColumnMap = Collections.emptyMap();

    private String m_workflowPath;

    /** @return the parameterToJsonConfigMap */
    public Map<String, ExternalNodeData> getParameterToJsonConfigMap() {
        return m_parameterToJsonConfigMap;
    }

    /** @param map the parameterToJsonConfigMap to set */
    public CallWorkflowConfiguration setParameterToJsonConfigMap(final Map<String, ExternalNodeData> map) {
        CheckUtils.checkArgumentNotNull(map, "must not be null");
        m_parameterToJsonConfigMap = map;
        return this;
    }

    /** @return the parameterToJsonColumnMap */
    public Map<String, String> getParameterToJsonColumnMap() {
        return m_parameterToJsonColumnMap;
    }

    /** @param map the parameterToJsonColumnMap to set */
    public CallWorkflowConfiguration setParameterToJsonColumnMap(final Map<String, String> map) {
        m_parameterToJsonColumnMap = map;
        return this;
    }

    /** @return the workflowPath */
    public String getWorkflowPath() {
        return m_workflowPath;
    }

    /** @param workflowPath the workflowPath to set */
    public CallWorkflowConfiguration setWorkflowPath(final String workflowPath) throws InvalidSettingsException {
        m_workflowPath = workflowPath;
        return this;
    }

    public CallWorkflowConfiguration save(final NodeSettingsWO settings) {
        settings.addString("workflow", m_workflowPath);
        NodeSettingsWO settings2 = settings.addNodeSettings("parameterToJsonConfigMap");
        for (Map.Entry<String, ExternalNodeData> entry : m_parameterToJsonConfigMap.entrySet()) {
            NodeSettingsWO childSettings = settings2.addNodeSettings(entry.getKey());
            String json = entry.getValue().getJSONObject().toString();
            childSettings.addString("json", json);
        }
        NodeSettingsWO settings3 = settings.addNodeSettings("parameterToJsonColumnMap");
        for (Map.Entry<String, String> entry : m_parameterToJsonColumnMap.entrySet()) {
            NodeSettingsWO childSettings = settings3.addNodeSettings(entry.getKey());
            childSettings.addString("json-column", entry.getValue());
        }
        return this;
    }

    public CallWorkflowConfiguration loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowPath = settings.getString("workflow");
        NodeSettingsRO settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        for (String s : settings2.keySet()) {
            NodeSettingsRO childSettings = settings2.getNodeSettings(s);
            String json = childSettings.getString("json");
            try {
                JsonObject object = JSONUtil.parseJSONValue(json);
                m_parameterToJsonConfigMap.put(s, ExternalNodeData.builder(s).jsonObject(object).build());
            } catch (JsonException ex) {
                throw new InvalidSettingsException("Invalid JSON string: " + ex.getMessage());
            }
        }

        NodeSettingsRO settings3 = settings.getNodeSettings("parameterToJsonColumnMap");
        m_parameterToJsonColumnMap = new LinkedHashMap<>();
        for (String s : settings3.keySet()) {
            NodeSettingsRO childSettings = settings3.getNodeSettings(s);
            String jsonColumn = childSettings.getString("json-column");
            m_parameterToJsonColumnMap.put(s, jsonColumn);
        }
        return this;
    }

    public CallWorkflowConfiguration loadInDialog(final NodeSettingsRO settings) {
        m_workflowPath = settings.getString("workflow", "");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        m_parameterToJsonColumnMap = new LinkedHashMap<>();
        NodeSettingsRO settings2;
        try {
            settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        } catch (InvalidSettingsException e) {
            return this;
        }
        for (String s : settings2.keySet()) {
            NodeSettingsRO childSettings;
            try {
                childSettings = settings2.getNodeSettings(s);
            } catch (InvalidSettingsException e) {
                continue;
            }
            String json = childSettings.getString("json", "{}");
            JsonObject object;
            try {
                object = JSONUtil.parseJSONValue(json);
            } catch (JsonException e) {
                continue;
            }
            m_parameterToJsonConfigMap.put(s, ExternalNodeData.builder(s).jsonObject(object).build());
        }

        NodeSettingsRO settings3;
        try {
            settings3 = settings.getNodeSettings("parameterToJsonColumnMap");
        } catch (InvalidSettingsException e) {
            return this;
        }

        for (String s : settings3.keySet()) {
            try {
                NodeSettingsRO childSettings = settings3.getNodeSettings(s);
                String jsonColumn = childSettings.getString("json-column");
                m_parameterToJsonColumnMap.put(s, jsonColumn);
            } catch (InvalidSettingsException e) {
                continue;
            }
        }

        return this;
    }
}
