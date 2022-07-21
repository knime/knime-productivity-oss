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
package org.knime.workflowservices.json.row.caller;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.json.util.JSONUtil;

/**
 * Config object to node. Holds (remote) workflow URI and parameters.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
@Deprecated(since = "4.7")
public abstract class CallWorkflowConfiguration {
    private Map<String, ExternalNodeData> m_parameterToJsonConfigMap = Collections.emptyMap();

    private Map<String, String> m_parameterToJsonColumnMap = Collections.emptyMap();

    private String m_workflowPath;

    private RptOutputFormat m_reportFormatOrNull;

    private boolean m_useQualifiedParameterNames;

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

    /** If set <code>true</code> it will use fully qualified parameter names. Such as "string-input-7" instead
     * of "string-input". Names will be fully qualified when they short name would be ambiguous.
     * @param value  the value. Default is false as of KNIME 3.5.
     * @return this
     * @since 3.5
     */
    public CallWorkflowConfiguration setUseQualifiedParameterNames(final boolean value) {
        m_useQualifiedParameterNames = value;
        return this;
    }

    /** @return see {@link #setUseQualifiedParameterNames(boolean)}
     * @since 3.5
     */
    public boolean isUseQualifiedParameterNames() {
        return m_useQualifiedParameterNames;
    }

    /** @return the reportFormatOrNull ... */
    public RptOutputFormat getReportFormatOrNull() {
        return m_reportFormatOrNull;
    }

    /** @param reportFormatOrNull the reportFormatOrNull to set
     * @eturn this */
    public CallWorkflowConfiguration setReportFormatOrNull(final RptOutputFormat reportFormatOrNull)
            throws InvalidSettingsException {
        CheckUtils.checkArgument(!RptOutputFormat.HTML.equals(reportFormatOrNull),
            "Rendering HTML not supported (image location unclear)");
        m_reportFormatOrNull = reportFormatOrNull;
        return this;
    }

    public CallWorkflowConfiguration save(final NodeSettingsWO settings) {
        settings.addString("workflow", m_workflowPath);
        settings.addString("reportFormatOrNull", Objects.toString(m_reportFormatOrNull, null));
        settings.addBoolean("useFullyQualifiedParameterNames", m_useQualifiedParameterNames);
        NodeSettingsWO settings2 = settings.addNodeSettings("parameterToJsonConfigMap");
        for (Map.Entry<String, ExternalNodeData> entry : m_parameterToJsonConfigMap.entrySet()) {
            NodeSettingsWO childSettings = settings2.addNodeSettings(entry.getKey());
            String json = entry.getValue().getJSONValue().toString();
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
        final String reportString = settings.getString("reportFormatOrNull", null); // added in 3.2
        if (StringUtils.isBlank(reportString)) {
            m_reportFormatOrNull = null;
        } else {
            try {
                setReportFormatOrNull(RptOutputFormat.valueOf(reportString));
            } catch (IllegalArgumentException e) {
                throw new InvalidSettingsException("Unsupported report format '" + reportString + "'", e);
            }
        }
        m_useQualifiedParameterNames = settings.getBoolean("useFullyQualifiedParameterNames", true);
        NodeSettingsRO settings2 = settings.getNodeSettings("parameterToJsonConfigMap");
        m_parameterToJsonConfigMap = new LinkedHashMap<>();
        for (String s : settings2.keySet()) {
            NodeSettingsRO childSettings = settings2.getNodeSettings(s);
            String json = childSettings.getString("json");
            try {
                JsonValue object = JSONUtil.parseJSONValue(json);
                m_parameterToJsonConfigMap.put(s, ExternalNodeData.builder(s).jsonValue(object).build());
            } catch (IOException ex) {
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
        final String reportString = settings.getString("reportFormatOrNull", null);
        if (StringUtils.isBlank(reportString)) {
            m_reportFormatOrNull = null;
        } else {
            try {
                setReportFormatOrNull(RptOutputFormat.valueOf(reportString));
            } catch (IllegalArgumentException | InvalidSettingsException e) {
                m_reportFormatOrNull = null;
            }
        }
        m_useQualifiedParameterNames = settings.getBoolean("useFullyQualifiedParameterNames", false);
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
            JsonValue value;
            try {
                value = JSONUtil.parseJSONValue(json);
            } catch (IOException e) {
                continue;
            }
            m_parameterToJsonConfigMap.put(s, ExternalNodeData.builder(s).jsonValue(value).build());
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
