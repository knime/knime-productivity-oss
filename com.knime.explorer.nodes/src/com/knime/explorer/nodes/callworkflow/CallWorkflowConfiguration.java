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

import com.knime.explorer.nodes.callworkflow.RemoteWorkflowBackend.Lookup;

/**
 * Config object to node. Holds (remote) workflow URI and parameters.
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class CallWorkflowConfiguration {

    private final boolean m_isRemote;
    private Map<String, JsonObject> m_parameterToJsonConfigMap = Collections.emptyMap();
    private Map<String, String> m_parameterToJsonColumnMap = Collections.emptyMap();
    private String m_workflowPath;
    private String m_remoteHostAndPort;
    private String m_username;
    private String m_password;

    /** @param isRemote */
    CallWorkflowConfiguration(final boolean isRemote) {
        m_isRemote = isRemote;
    }
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

    /** @return the remoteHostAndPort */
    String getRemoteHostAndPort() {
        return m_remoteHostAndPort;
    }
    /** @param remoteHostAndPort the remoteHostAndPort to set */
    void setRemoteHostAndPort(final String remoteHostAndPort) {
        m_remoteHostAndPort = remoteHostAndPort;
    }
    /** @return the username */
    String getUsername() {
        return m_username;
    }
    /** @param username the username to set */
    void setUsername(final String username) {
        m_username = username;
    }
    /** @return the password */
    String getPassword() {
        return m_password;
    }
    /** @param password the password to set */
    void setPassword(final String password) {
        m_password = password;
    }

    void save(final NodeSettingsWO settings) {
        if (m_isRemote) {
            settings.addString("remoteHostAndPort", m_remoteHostAndPort);
            settings.addString("username", m_username);
            settings.addPassword("password", "GladYouFoundIt", m_password);
        }
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
        if (m_isRemote) {
            m_remoteHostAndPort = settings.getString("remoteHostAndPort");
            CheckUtils.checkSetting(StringUtils.isNotBlank(m_remoteHostAndPort), "Host must not be empty");
            m_username = settings.getString("username");
            CheckUtils.checkSetting(StringUtils.isNotBlank(m_username), "User must not be empty");
            m_password = settings.getPassword("password", "GladYouFoundIt");
        }
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
        if (m_isRemote) {
            m_remoteHostAndPort = settings.getString("remoteHostAndPort", null);
            m_remoteHostAndPort = StringUtils.defaultIfBlank(m_remoteHostAndPort,
                "http://localhost:8080/com.knime.enterprise.server/rest");
            m_username = settings.getString("username", System.getProperty("user.name"));
            m_password = settings.getPassword("password", "GladYouFoundIt", "");
        }
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

    IWorkflowBackend newBackend() throws Exception {
        if (m_isRemote) {
            return RemoteWorkflowBackend.newInstance(
                Lookup.newLookup(m_remoteHostAndPort, m_workflowPath, m_username, m_password));
        } else {
            return LocalWorkflowBackend.get(m_workflowPath);
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
