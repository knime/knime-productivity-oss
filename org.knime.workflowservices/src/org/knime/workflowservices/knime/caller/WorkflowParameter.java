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
 */
package org.knime.workflowservices.knime.caller;

import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;

/**
 * Combines a workflow parameter (name and description) and its port type.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public final class WorkflowParameter {

    private static final String CFG_PORT_TYPE = "portType";
    private static final String CFG_PARAMETER_NAME = "parameterName";
    private static final String CFG_PARAMETER_DESCRIPTION = "parameterDescription";

    /** {@link #getParameterName()} */
    private final String m_parameterName;

    /**
     * The {@code null}able parameter description.
     */
    private final String m_parameterDescription;

    /** {@link #getPortType()} */
    private final PortType m_portType;

    /**
     * Creates a new workflow parameter.
     *
     * @param parameterName parameter name chosen by the user, e.g., input-parameter (as entered in the Workflow
     *            Input or Output dialog) or as modified by the framework (to make non-unique parameter names unique),
     *            e.g., input-parameter-1
     * @param parameterDescription optional description set by the user
     * @param portType the port type of the Workflow Input or Output node
     * @throws InvalidSettingsException if the port type is null or the parameter name is invalid
     */
    public WorkflowParameter(final String parameterName, final String parameterDescription, final PortType portType)
        throws InvalidSettingsException {
        m_parameterName = parameterName;
        m_parameterDescription = parameterDescription;
        m_portType = CheckUtils.checkSettingNotNull(portType, "PortType must not be null");
    }

    /**
     * @return what this particular callee workflow input/output expects, e.g., a deep learning model, etc.
     */
    public PortType getPortType() {
        return m_portType;
    }

    /**
     * @return The name used to uniquely identify one of the input parameter/return value of a Workflow Input or a
     *         Workflow Output node.
     */
    public String getParameterName() {
        return m_parameterName;
    }

    /**
     * Returns the optional parameter description.
     *
     * @return description for the parameter if set
     */
    public Optional<String> getParameterDescription() {
        return Optional.ofNullable(m_parameterDescription);
    }

    /**
     * Saves object to argument, used during 'save' in model or dialog.
     *
     * @param settings To save to.
     */
    public void saveTo(final NodeSettingsWO settings) {
        settings.addString(CFG_PARAMETER_NAME, m_parameterName);
        settings.addString(CFG_PARAMETER_DESCRIPTION, m_parameterDescription);
        var portSettings = settings.addNodeSettings(CFG_PORT_TYPE);
        m_portType.save(portSettings);
    }

    /**
     * Loads new object from settings, previously saved with {@link #saveTo(NodeSettingsWO)}.
     *
     * @param settings to load from
     * @return a new object loaded from the settings
     * @throws InvalidSettingsException Settings invalid or port type can't be restored
     */
    public static WorkflowParameter loadFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        var parameterName = settings.getString(CFG_PARAMETER_NAME);
        final var parameterDescription = settings.getString(CFG_PARAMETER_DESCRIPTION, null);
        var portSettings = settings.getNodeSettings(CFG_PORT_TYPE);
        var portType = PortType.load(portSettings);
        // doesn't validate parameter names here (they might have been changed by the framework to make them unique)
        return new WorkflowParameter(parameterName, parameterDescription, portType);
    }

    @Override
    public String toString() {
        return String.format("%s (%s): \"%s\"", m_parameterName, m_portType.getName(), m_parameterDescription);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, true);
    }

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, true);
    }

}
