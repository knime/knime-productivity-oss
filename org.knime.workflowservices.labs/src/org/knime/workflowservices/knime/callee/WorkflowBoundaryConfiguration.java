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
 *   21.04.2021 (loescher): created
 */
package org.knime.workflowservices.knime.callee;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.workflowservices.knime.CalleeWorkflowData;

/**
 * Common configuration for the Workflow Input and Workflow Output nodes.
 *
 * Uses the {@link CalleeWorkflowData}'s JSON serialization mechanism for loading and saving settings, by storing
 * them into a single String field in the NodeSettings.
 */
public final class WorkflowBoundaryConfiguration {

    private static final String CFG_PARAMETER_NAME = "parameterName";

    /**
     * New Workflow Input nodes are initialized with one output port of this type. New Workflow Output nodes are
     * initialized with one input port of this type.
     */
    static final PortType DEFAULT_PORT_TYPE = BufferedDataTable.TYPE;

    static final PortType[] ALL_PORT_TYPES = PortTypeRegistry.getInstance()//
        .availablePortTypes()//
        .toArray(PortType[]::new);

    private String m_parameterName;

    /**
     * @param parameterName
     */
    WorkflowBoundaryConfiguration(final String parameterName) {
        m_parameterName = parameterName;
    }

    /**
     * Saves the settings of this configuration to the given settings object.
     *
     * @param settings the settings to save to.
     */
    void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString(CFG_PARAMETER_NAME, m_parameterName);
    }

    /**
     * Loads the validated settings from the given settings object.
     *
     * @param settings the settings from which to load.
     * @return this
     */
    WorkflowBoundaryConfiguration loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        var parameterName = settings.getString(CFG_PARAMETER_NAME);
        m_parameterName = CalleeWorkflowData.validateParameterName(parameterName);
        return this;
    }

    /**
     * @return the parameterName
     */
    String getParameterName() {
        return m_parameterName;
    }

    /**
     * @param parameterName the parameterName to set
     * @return this (method chaining)
     * @throws InvalidSettingsException if arg doesn't follow pattern
     */
    WorkflowBoundaryConfiguration setParameterName(final String parameterName) throws InvalidSettingsException {
        m_parameterName = CalleeWorkflowData.validateParameterName(parameterName);
        return this;
    }

    public static void saveConfigAsNodeSettings(final NodeSettingsWO settings, final String parameterName) {
        var config = new WorkflowBoundaryConfiguration(parameterName);
        config.saveSettingsTo(settings);
    }

}
