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

import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;

/**
 * Config object to node.
 * @author wiswedel
 */
final class CallWorkflowConfiguration {

    private Map<String, JsonObject> m_parameterToJsonConfigMap = Collections.emptyMap();
    private Map<String, String> m_parameterToJsonColumnMap = Collections.emptyMap();
    private String m_workflowPath;

    /** @return the parameterToJsonConfigMap */
    Map<String, JsonObject> getParameterToJsonConfigMap() {
        return m_parameterToJsonConfigMap;
    }
    /** @param map the parameterToJsonConfigMap to set */
    void setParameterToJsonConfigMap(final Map<String, JsonObject> map) {
        CheckUtils.checkArgumentNotNull(map, "must not be null");
        m_parameterToJsonConfigMap = map;
    }
    /** @return the parameterToJsonColumnMap */
    Map<String, String> getParameterToJsonColumnMap() {
        return m_parameterToJsonColumnMap;
    }
    /** @param map the parameterToJsonColumnMap to set */
    void setParameterToJsonColumnMap(final Map<String, String> map) {
        CheckUtils.checkArgumentNotNull(map, "must not be null");
        m_parameterToJsonColumnMap = map;
    }

    /** @return the workflowPath */
    String getWorkflowPath() {
        return m_workflowPath;
    }
    /** @param workflowPath the workflowPath to set */
    void setWorkflowPath(final String workflowPath) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotBlank(workflowPath) , "must not be null or empty");
        m_workflowPath = workflowPath;
    }

    void save(final NodeSettingsWO settings) {
        settings.addString("workflow", m_workflowPath);
        NodeSettingsWO settings2 = settings.addNodeSettings("parameterToJsonConfigMap");
        for (Map.Entry<String, JsonObject> entry : m_parameterToJsonConfigMap.entrySet()) {
            NodeSettingsWO childSettings = settings2.addNodeSettings(entry.getKey());
            String json = entry.getValue().toString();
            childSettings.addString("json", json);
        }
        NodeSettingsWO settings3 = settings.addNodeSettings("parameterToJsonColumnMap");
        for (Map.Entry<String, String> entry : m_parameterToJsonColumnMap.entrySet()) {
            NodeSettingsWO childSettings = settings3.addNodeSettings(entry.getKey());
            childSettings.addString("json-column", entry.getValue());
        }
    }

    CallWorkflowConfiguration loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowPath = settings.getString("workflow");
        NodeSettingsRO settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        for (String s : settings2.keySet()) {
            NodeSettingsRO childSettings = settings2.getNodeSettings(s);
            String json = childSettings.getString("json");
            JsonObject object = readJSONObject(json);
            m_parameterToJsonConfigMap.put(s,  object);
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

    void loadInDialog(final NodeSettingsRO settings) {
        m_workflowPath = settings.getString("workflow", "");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        m_parameterToJsonColumnMap = new LinkedHashMap<>();
        NodeSettingsRO settings2;
        try {
            settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        } catch (InvalidSettingsException e) {
            return;
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
                object = readJSONObject(json);
            } catch (InvalidSettingsException e) {
                continue;
            }
            m_parameterToJsonConfigMap.put(s,  object);
        }

        NodeSettingsRO settings3;
        try {
            settings3 = settings.getNodeSettings("parameterToJsonColumnMap");
        } catch (InvalidSettingsException e) {
            return;
        }
        for (String s : settings3.keySet()) {
            try {
                NodeSettingsRO childSettings = settings3.getNodeSettings(s);
                String jsonColumn = childSettings.getString("json-column");
                m_parameterToJsonColumnMap.put(s,  jsonColumn);
            } catch (InvalidSettingsException e) {
                continue;
            }
        }
    }

    private JsonObject readJSONObject(final String json) throws InvalidSettingsException {
        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            return jsonReader.readObject();
        } catch (Exception e) {
            throw new InvalidSettingsException("Invalid JSON: " + e.getMessage(), e);
        }
    }

}
