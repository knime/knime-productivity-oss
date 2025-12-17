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
 * ------------------------------------------------------------------------
 */

package org.knime.workflowservices.knime.caller2;

import java.util.Arrays;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.context.ports.ExtendablePortGroup;
import org.knime.core.node.port.PortType;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.webui.node.dialog.NodeDialog.OnApplyNodeModifier;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.workflowservices.CallWorkflowParameters;
import org.knime.workflowservices.knime.caller2.ErrorAndLoadingUtil.HasWorkflowParametersErrorMessage;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;

/**
 * Node parameters for Call Workflow Service.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @author AI Migration Pipeline v1.2
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
final class CallWorkflow2NodeParameters implements NodeParameters {

    @PersistEmbedded
    CallWorkflowParameters m_callWorkflowParameters = new CallWorkflowParameters();

    @Persist(configKey = "calleeProperties")
    CalleeParameters m_calleeParameters = new CalleeParameters();

    /**
     * If present in the node settings and set to true, we invalidate the settings.
     */
    public static final String HAS_WORKFLOW_PARAMETERS_ERROR_CFG_KEY = "hasWorkflowParametersError";

    @ValueProvider(HasWorkflowParametersErrorMessage.class)
    @ValueReference(HasWorkflowParametersErrorMessage.class)
    @Persist(configKey = HAS_WORKFLOW_PARAMETERS_ERROR_CFG_KEY)
    boolean m_hasWorkflowParametersError;

    static class UpdatePortsOnApplyModifier implements OnApplyNodeModifier {

        @Override
        public void onApply(final NativeNodeContainer nnc, final NodeSettingsRO previousModelSettings,
            final NodeSettingsRO updatedModelSettings, final NodeSettingsRO previousViewSettings,
            final NodeSettingsRO updatedViewSettings) {

            final CallWorkflow2NodeParameters updatedParameters;
            try {
                updatedParameters =
                    NodeParametersUtil.loadSettings(updatedModelSettings, CallWorkflow2NodeParameters.class);
            } catch (InvalidSettingsException e) {
                /**
                 * This should not happen since settings are being validated first.
                 */
                throw new IllegalStateException("Failed to load updated parameters", e);
            }

            var nodeCreationConfig = nnc.getNode().getCopyOfCreationConfig()
                .orElseThrow(() -> new IllegalStateException("Node creation config is not present"));

            var portConfig = nodeCreationConfig.getPortConfig()
                .orElseThrow(() -> new IllegalStateException("Port config is not present"));

            ExtendablePortGroup inputConfig = (ExtendablePortGroup)portConfig
                .getGroup(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
            final var currentInputPortTypes = inputConfig.getConfiguredPorts();

            ExtendablePortGroup outputConfig = (ExtendablePortGroup)portConfig
                .getGroup(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
            final var currentOutputPortTypes = outputConfig.getConfiguredPorts();

            final var targetInPortTypes = updatedParameters.m_calleeParameters.m_inputParameters.stream()
                .map(WorkflowParameterElement::getPortType).toArray(PortType[]::new);

            final var targetOutPortTypes = updatedParameters.m_calleeParameters.m_outputParameters.stream()
                .map(WorkflowParameterElement::getPortType).toArray(PortType[]::new);

            final var inPortsMatch = Arrays.equals(currentInputPortTypes, targetInPortTypes);
            final var outPortsMatch = Arrays.equals(currentOutputPortTypes, targetOutPortTypes);

            if (inPortsMatch && outPortsMatch) {
                return; // No change
            }

            if (!inPortsMatch) {
                while (inputConfig.hasConfiguredPorts()) {
                    inputConfig.removeLastPort();
                }
                for (PortType type : targetInPortTypes) {
                    inputConfig.addPort(type);
                }
            }
            if (!outPortsMatch) {
                while (outputConfig.hasConfiguredPorts()) {
                    outputConfig.removeLastPort();
                }
                for (PortType type : targetOutPortTypes) {
                    outputConfig.addPort(type);
                }
            }

            var wfm = nnc.getParent();
            wfm.replaceNode(nnc.getID(), nodeCreationConfig);
        }

    }

}
