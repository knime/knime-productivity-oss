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

import java.io.File;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.api.NodeSettingsPersistor;
import org.knime.workflowservices.knime.caller.WorkflowParameter;
import org.knime.workflowservices.knime.util.CallWorkflowUtil;

/**
 * Common configuration for the Workflow Input and Workflow Output nodes.
 */
@SuppressWarnings("restriction") // webui not API yet
abstract class WorkflowBoundaryConfiguration implements DefaultNodeSettings {

    /**
     * New Workflow Input nodes are initialized with one output port of this type. New Workflow Output nodes are
     * initialized with one input port of this type.
     */
    static final PortType DEFAULT_PORT_TYPE = BufferedDataTable.TYPE;

    // always fresh, because after installing extensions, a restart is necessary
    static final PortType[] ALL_PORT_TYPES = PortTypeRegistry.getInstance()//
        .availablePortTypes()//
        .toArray(PortType[]::new);

    // backwards compatibility
    static final class OptionalParameterDescription implements NodeSettingsPersistor<String> {

        private static final String CFG_PARAMETER_DESCRIPTION = "parameterDescription";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return settings.getString(CFG_PARAMETER_DESCRIPTION, "");
        }

        @Override
        public void save(final String desc, final NodeSettingsWO settings) {
            settings.addString(CFG_PARAMETER_DESCRIPTION, desc);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][] {{ CFG_PARAMETER_DESCRIPTION }};
        }
    }

    abstract String getParameterName();

    /**
     * @param parameterName the parameterName to set
     * @return this (method chaining)
     * @throws InvalidSettingsException if arg doesn't follow pattern
     */
    abstract WorkflowBoundaryConfiguration setParameterName(final String parameterName) throws InvalidSettingsException;

    abstract String getParameterDescription();

    /**
     * Creates external node data from the settings and the given port type.
     *
     * @param portType non-{@code null} port type to use
     * @param portContent {@code null}able file with the port content
     * @return external node data
     */
    ExternalNodeData toExternalNodeData(final PortType portType, final File portContent) {
        // this method was previously "duplicated" in WorklowInputNodeModel and WorkflowOutputNodeModel and was prone
        // to NPE if the port type was null:
        // down the line through createExternalNodeData->ResourceContentType.of(null portType)
        CheckUtils.checkNotNull(portType);

        try {
            return CallWorkflowUtil.createExternalNodeData(
                new WorkflowParameter(getParameterName(), getParameterDescription(), portType), portContent);
        } catch (final InvalidSettingsException e) {
            // The cause for this InvalidSettingsException is a null port type. Previously `getOutPortType(0)` was
            // directly passed to `createExternalNodeData`, which would access a method on the given port type without
            // a null check.
            // Hence, we would have already gotten an NPE if the output port type was null before this change.
            // We catch this condition earlier now (above) and rethrow any other ISE that might occur here as unchecked
            // to adhere to the method interface.
            throw ExceptionUtils.asRuntimeException(e);
        }
    }

}
