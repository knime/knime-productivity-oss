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
 *   Jan 7, 2026 (paulbaernreuther): created
 */
package org.knime.workflowservices.knime.caller2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.knime.workflowservices.CallWorkflowParametersTestUtils.setDummyWorkflowSelection;
import static org.knime.workflowservices.CallWorkflowParametersTestUtils.setFinishedProcessingUuidAndAssertFinishLoading;
import static org.knime.workflowservices.CallWorkflowParametersTestUtils.setLastProcessingUuid;
import static org.knime.workflowservices.CallWorkflowParametersTestUtils.setLastProcessingUuidCopyAndAssertStartLoading;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.testing.node.dialog.updates.DialogUpdateSimulator;
import org.knime.testing.node.dialog.updates.UpdateSimulator.UpdateSimulatorResult;
import org.knime.testing.util.WorkflowManagerUtil;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowExecutionConnectorProvider;
import org.knime.workflowservices.CallWorkflowParametersTestUtils;
import org.knime.workflowservices.IWorkflowBackend.ResourceContentType;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Test class for CallWorkflow2Node dialog updates.
 */
@SuppressWarnings("restriction")
final class CallWorkflow2NodeDialogUpdateTest {

    private static final String CALL_WORKFLOW_PARAMETERS = "callWorkflowParameters";

    private MockedStatic<ConnectionUtil> m_mockStaticConnectionUtil;

    private CallWorkflow2NodeDialogUpdateTest() {
        // Private constructor to prevent instantiation
    }

    private WorkflowManager m_wfm;

    @BeforeEach
    void mockWorkflowBackend() throws IOException {
        m_wfm = WorkflowManagerUtil.createEmptyWorkflow();
        m_mockStaticConnectionUtil = mockStatic(ConnectionUtil.class);
    }

    @Test
    void testLoadsInputAndOutputParametersFromWorkflowBackend() throws IOException, InvalidSettingsException {
        mockTwoInputsAndOneOutput();
        final var nc = WorkflowManagerUtil.createAndAddNode(m_wfm, new CallWorkflow2NodeFactory());
        NodeContext.pushContext(nc);
        try {
            final var nodeParameters = new CallWorkflow2NodeParameters();
            setDummyWorkflowSelection(nodeParameters.m_callWorkflowParameters);
            /**
             * Uses the node context to get the port configuration and port types from the node container.
             */
            final var context = NodeParametersUtil.createDefaultNodeSettingsContext(new PortObjectSpec[]{});
            final var updateSimulator = new DialogUpdateSimulator(Map.of(SettingsType.MODEL, nodeParameters), context);
            final var initialResult = updateSimulator.simulateAfterOpenDialog();
            final var fromProcessingUuidResult =
                setLastProcessingUuid(nodeParameters.m_callWorkflowParameters, initialResult, CALL_WORKFLOW_PARAMETERS)
                    .apply(updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID()))
                .as("We wait until the processing uuid is copied before accessing the workflow backend.").isZero();
            final var fromCopiedProcessingUuidResult =
                setLastProcessingUuidCopyAndAssertStartLoading(nodeParameters.m_callWorkflowParameters,
                    fromProcessingUuidResult, CALL_WORKFLOW_PARAMETERS).apply(updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID()))
                .as("The loading thread should be started after copying the processing uuid.").isOne();
            assertTwoInputsAndOneOutput(fromCopiedProcessingUuidResult);
            setFinishedProcessingUuidAndAssertFinishLoading(nodeParameters.m_callWorkflowParameters,
                fromCopiedProcessingUuidResult, CALL_WORKFLOW_PARAMETERS, updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID()))
                .as("The loading thread should be finished after setting the finished processing uuid.").isZero();
        } finally {
            NodeContext.removeLastContext();
        }

    }

    @Test
    void testSecondUpdateCancelsFirstOne() throws IOException, InvalidSettingsException {
        mockTwoInputsAndOneOutput();
        final var nc = WorkflowManagerUtil.createAndAddNode(m_wfm, new CallWorkflow2NodeFactory());
        NodeContext.pushContext(nc);
        try {
            final var nodeParameters = new CallWorkflow2NodeParameters();
            setDummyWorkflowSelection(nodeParameters.m_callWorkflowParameters);
            final var context = NodeParametersUtil.createDefaultNodeSettingsContext(new PortObjectSpec[]{});
            final var updateSimulator = new DialogUpdateSimulator(Map.of(SettingsType.MODEL, nodeParameters), context);
            final var initialResult = updateSimulator.simulateAfterOpenDialog();
            final var fromProcessingUuidResult =
                setLastProcessingUuid(nodeParameters.m_callWorkflowParameters, initialResult, CALL_WORKFLOW_PARAMETERS)
                    .apply(updateSimulator);
            setLastProcessingUuidCopyAndAssertStartLoading(nodeParameters.m_callWorkflowParameters,
                fromProcessingUuidResult, CALL_WORKFLOW_PARAMETERS).apply(updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID())).isOne();
            final var fromCopiedProcessingUuidResult2 =
                setLastProcessingUuidCopyAndAssertStartLoading(nodeParameters.m_callWorkflowParameters,
                    fromProcessingUuidResult, CALL_WORKFLOW_PARAMETERS).apply(updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID())).isOne();
            setFinishedProcessingUuidAndAssertFinishLoading(nodeParameters.m_callWorkflowParameters,
                fromCopiedProcessingUuidResult2, CALL_WORKFLOW_PARAMETERS, updateSimulator);
            assertThat(WorkflowExecutionConnectorProvider.getNumRunningThreads(nc.getID())).isZero();
        } finally {
            NodeContext.removeLastContext();
        }
    }

    @SuppressWarnings("resource")
    private void mockTwoInputsAndOneOutput() throws IOException, InvalidSettingsException {
        final var workflowBackendMock =
            CallWorkflowParametersTestUtils.createWorkflowBackendMock(m_mockStaticConnectionUtil);
        final Map<String, ResourceContentType> inputs = new LinkedHashMap<>();
        inputs.put("firstInput", ResourceContentType.of(BufferedDataTable.TYPE));
        inputs.put("secondInput", ResourceContentType.of(FlowVariablePortObject.TYPE));
        Mockito.when(workflowBackendMock.getInputResourceDescription()).thenReturn(inputs);
        final Map<String, ResourceContentType> outputs = new LinkedHashMap<>();
        outputs.put("firstOutput", ResourceContentType.of(BufferedDataTable.TYPE));
        Mockito.when(workflowBackendMock.getOutputResourceDescription()).thenReturn(outputs);
    }

    @SuppressWarnings("unchecked")
    private static void assertTwoInputsAndOneOutput(final UpdateSimulatorResult fromCopiedProcessingUuidResult) {
        final var updatedInputParameters =
            (List<InputParametersUtil.InputParameterElement>)fromCopiedProcessingUuidResult
                .getValueUpdateAt("calleeParameters", "inputParameters");
        assertThat(updatedInputParameters).hasSize(2);
        assertThat(updatedInputParameters.get(0).m_parameterName).isEqualTo("firstInput");
        assertThat(updatedInputParameters.get(0).getPortType()).isEqualTo(BufferedDataTable.TYPE);
        assertThat(updatedInputParameters.get(1).m_parameterName).isEqualTo("secondInput");
        assertThat(updatedInputParameters.get(1).getPortType()).isEqualTo(FlowVariablePortObject.TYPE);
        final var updatedOutputParameters =
            (List<OutputParametersUtil.OutputParameterElement>)fromCopiedProcessingUuidResult
                .getValueUpdateAt("calleeParameters", "outputParameters");
        assertThat(updatedOutputParameters).hasSize(1);
        assertThat(updatedOutputParameters.get(0).m_parameterName).isEqualTo("firstOutput");
        assertThat(updatedOutputParameters.get(0).getPortType()).isEqualTo(BufferedDataTable.TYPE);
    }

    /**
     * Resets and closes all mocks after each test.
     */
    @AfterEach
    public void resetMocks() {
        m_mockStaticConnectionUtil.close();
    }
}
