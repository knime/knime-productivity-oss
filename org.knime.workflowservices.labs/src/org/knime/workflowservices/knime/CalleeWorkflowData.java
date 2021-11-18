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
package org.knime.workflowservices.knime;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.DialogNode;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;

// TODO javadoc
/**
 * Payload for {@link ExternalNodeData} objects used to communicate between caller and callee workflow. Instances of
 * this class are used for several purposes:
 *
 * <ul>
 * <li>communicate to caller the expected input for a callee workflow input node</li>
 * <li>communicate to caller what a callee workflow output node returns</li>
 * <li>receive from caller actual inputs for a callee workflow input node to be executed</li>
 * <li>send to caller actual outputs from an executed callee workflow output node</li>
 * </ul>
 *
 * The communication part is done by first calling {@link IWorkflowBackend#getInputNodes()} and
 * {@link IWorkflowBackend#getOutputValues()}. This returns {@link ExternalNodeData} and {@link JSONValue},
 * respectively. Both contain instances of this class to describe the kind of data using {@link #getPortType()}. <br/>
 * <br/>
 * The execution part comprises two steps. The inputs for the callee workflow are then prepared and set using
 * {@link IWorkflowBackend#setInputNodes(java.util.Map)}. The sent {@link ExternalNodeData} objects contain instances of
 * this class pointing to a file that contains the data for the workflow input nodes. Then,
 * {@link IWorkflowBackend#execute(java.util.Map)} is used to execute the callee workflow and
 * {@link IWorkflowBackend#getOutputValues()} is called again, this time the returned {@link JSONValue}s contain an
 * instance of this class that also contain <br/>
 * <br/>
 * JSON-serializable for convenient storage in {@link ExternalNodeData} or {@link JSONValue}. <br/>
 * <br/>
 * Immutable.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public final class CalleeWorkflowData {

    private static final String CFG_PORT_TYPE = "portType";
    private static final String CFG_PARAMETER_NAME = "parameterName";

    /**
     * Used to determine whether the identifier for a callee workflow parameter is valid: not null and not empty.
     */
    public static final Predicate<String> PARAMETER_IDENTIFIER_IS_VALID = //
        ((Predicate<String>)Objects::nonNull)//
            .and(Predicate.not(String::isEmpty))//
            .and(DialogNode.PARAMETER_NAME_PATTERN.asMatchPredicate());

    public static final UnaryOperator<String> PARAMETER_IDENTIFIER_ERROR_MESSAGE =
        parameterName -> String.format("Invalid parameter name: \"%s\". "
            + "Valid parameter names consist of one or several strings, separated by dashes or underscores. "
            + "Parameter names must not end with a digit. "
            + "For instance, input1 is not a valid parameter name, but input1-table is. ", parameterName);

    /** {@link #getParameterName()} */
    private final String m_parameterName;

    /** {@link #getPortType()} */
    private final PortType m_portType;

    /**
     * TODO port type might as well be a ResourceContentType
     *
     * @param parameterName parameter name chosen by the user, e.g., input-parameter (as entered in the Workflow Service
     *            Input or Output dialog) or as modified by the framework (to make non-unique parameter names unique),
     *            e.g., input-parameter-1
     * @param portType the port type of the Workflow Input or Output node
     * @throws InvalidSettingsException if the port type is null or the parameter name is invalid
     */
    public CalleeWorkflowData(final String parameterName, final PortType portType) throws InvalidSettingsException {
        m_parameterName = parameterName;
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
     * Saves object to argument, used during 'save' in model or dialog.
     *
     * @param settings To save to.
     */
    public void saveTo(final NodeSettingsWO settings) {
        settings.addString(CFG_PARAMETER_NAME, m_parameterName);
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
    public static CalleeWorkflowData loadFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        var parameterName = settings.getString(CFG_PARAMETER_NAME);
        var portSettings = settings.getNodeSettings(CFG_PORT_TYPE);
        var portType = PortType.load(portSettings);
        // doesn't validate parameter names here (they might have been changed by the framework to make them unique)
        return new CalleeWorkflowData(parameterName, portType);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", m_parameterName, m_portType.getClass().getName());
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, true);
    }

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, true);
    }

    public static String validateParameterName(final String parameterName) throws InvalidSettingsException {
        CheckUtils.checkSetting(PARAMETER_IDENTIFIER_IS_VALID.test(parameterName),
            PARAMETER_IDENTIFIER_ERROR_MESSAGE.apply(parameterName));
        return parameterName;
    }

}
