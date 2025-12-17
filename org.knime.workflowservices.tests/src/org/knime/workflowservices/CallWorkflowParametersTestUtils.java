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
package org.knime.workflowservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelection;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.RelativeTo;
import org.knime.testing.node.dialog.updates.DialogUpdateSimulator;
import org.knime.testing.node.dialog.updates.UpdateSimulator.UpdateSimulatorResult;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Utility methods for testing CallWorkflowParameters related functionality.
 *
 * @author paulbaernreuther
 */
public class CallWorkflowParametersTestUtils {

    private CallWorkflowParametersTestUtils() {
        // prevent instantiation
    }

    private static final String DIALOG_STATE = "dialogState";

    /**
     * Use this method to initialize a CallWorkflowParameters with a dummy workflow selection for testing.
     *
     * @param callWorkflowParameters
     */
    @SuppressWarnings("restriction")
    public static void setDummyWorkflowSelection(final CallWorkflowParameters callWorkflowParameters) {
        callWorkflowParameters.m_common.m_workflowOrDeploymentSelection.m_runWorkflow.m_workflowPath =
            new FileSelection(
                new FSLocation(FSCategory.RELATIVE, RelativeTo.SPACE.getSettingsValue(), "dummy/path/to/workflow"));
        callWorkflowParameters.m_common.m_workflowOrDeploymentSelection.m_runWorkflow.m_executionContext =
            "dummyExecutionContextId";
    }

    /**
     * Use this method to create a mock IWorkflowBackend that is returned when creating a connection via ConnectionUtil.
     *
     * @param mockStaticConnectionUtil the mocked static ConnectionUtil
     * @return the mocked IWorkflowBackend
     * @throws IOException not expected
     */
    @SuppressWarnings("resource")
    public static IWorkflowBackend
        createWorkflowBackendMock(final MockedStatic<ConnectionUtil> mockStaticConnectionUtil) throws IOException {
        final var wec = Mockito.mock(WorkflowExecutionConnector.class);
        mockStaticConnectionUtil
            .when(() -> ConnectionUtil.createConnection(any(CallWorkflowConnectionConfiguration.class)))
            .thenReturn(Optional.of(wec));
        final var wb = Mockito.mock(IWorkflowBackend.class);
        Mockito.when(wec.createWorkflowBackend()).thenReturn(wb);
        return wb;
    }

    /**
     * Call this after an update of a {@link DialogUpdateSimulator} that is expected to change the lastProcessingUuid in
     * the dialog state.
     *
     * @param params the CallWorkflowParameters to update
     * @param result the result of the update simulation
     * @param callWokflowParametersKey the key of the CallWorkflowParameters in the current parameters
     * @return the transitive update triggered by this change
     */
    public static Function<DialogUpdateSimulator, UpdateSimulatorResult> setLastProcessingUuid(
        final CallWorkflowParameters params, final UpdateSimulatorResult result,
        final String callWokflowParametersKey) {
        final var lastProcessingUuid =
            (String)result.getValueUpdateAt(callWokflowParametersKey, DIALOG_STATE, "lastProcessingUuid");
        params.m_dialogState.m_lastProcessingUuid = lastProcessingUuid;
        return simulator -> simulator.simulateValueChange(callWokflowParametersKey, DIALOG_STATE, "lastProcessingUuid");
    }

    /**
     * Call this after an update of a {@link DialogUpdateSimulator} that is expected to change the
     * copiedLastProcessingUuid in the dialog state.
     *
     * @param params the CallWorkflowParameters to update
     * @param fromProcessingUuidResult the result of the update simulation
     * @param callWokflowParametersKey the key of the CallWorkflowParameters in the current parameters
     * @return the transitive update triggered by this change
     */
    public static Function<DialogUpdateSimulator, UpdateSimulatorResult> setLastProcessingUuidCopyAndAssertStartLoading(
        final CallWorkflowParameters params, final UpdateSimulatorResult fromProcessingUuidResult,
        final String callWokflowParametersKey) {

        assertThat(fromProcessingUuidResult.getValueUpdateAt(callWokflowParametersKey, "workflowConnectionIsLoading"))
            .as("Dialog should be set to loading once the processing uuid is copied to trigger subsequent computations.")
            .isEqualTo(Boolean.TRUE);

        final var copiedLastProcessingUuid = (String)fromProcessingUuidResult.getValueUpdateAt(callWokflowParametersKey,
            DIALOG_STATE, "copiedLastProcessingUuid");

        params.m_dialogState.m_copiedLastProcessingUuid = copiedLastProcessingUuid;
        return simulator -> simulator.simulateValueChange(callWokflowParametersKey, DIALOG_STATE,
            "copiedLastProcessingUuid");

    }

    /**
     * Call this after an update of a {@link DialogUpdateSimulator} that is expected to change the
     * finishedProcessingUuid in the dialog state.
     *
     * It will also trigger the transitive update and assert that the workflowConnectionIsLoading flag is set to false
     * after that.
     *
     * @param params the CallWorkflowParameters to update
     * @param fromCopiedProcessingUuidResult the result of the update simulation
     * @param callWokflowParametersKey the key of the CallWorkflowParameters in the current parameters
     * @param simulator the dialog update simulator to use for the transitive update
     */
    public static void setFinishedProcessingUuidAndAssertFinishLoading(final CallWorkflowParameters params,
        final UpdateSimulatorResult fromCopiedProcessingUuidResult, final String callWokflowParametersKey,
        final DialogUpdateSimulator simulator) {
        final var finishedProcessingUuid = (String)fromCopiedProcessingUuidResult
            .getValueUpdateAt(callWokflowParametersKey, DIALOG_STATE, "finishedProcessingUuid");
        params.m_dialogState.m_finishedProcessingUuid = finishedProcessingUuid;
        final var finalResult =
            simulator.simulateValueChange(callWokflowParametersKey, DIALOG_STATE, "finishedProcessingUuid");
        assertThat(finalResult.getValueUpdateAt(callWokflowParametersKey, "workflowConnectionIsLoading"))
            .as("Dialog should no longer be loading once the processing is finished.").isEqualTo(Boolean.FALSE);

    }

}
