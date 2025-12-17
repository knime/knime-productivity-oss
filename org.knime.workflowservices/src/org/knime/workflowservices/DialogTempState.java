/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
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
 *   Created on Dec 14, 2025 by paulbaernreuther
 */
package org.knime.workflowservices;

import java.util.UUID;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowExecutionConnectorProvider;
import org.knime.workflowservices.RunWorkflowParameters.WorkflowPathRef;
import org.knime.workflowservices.RunWorkflowParameters.WorkflowVersionRef;
import org.knime.workflowservices.WorkflowOrDeploymentSelectionParameters.DeploymentIdRef;

final class DialogTempState implements NodeParameters {

    /**
     * when any of the parameters affecting which workflow is called changes, we change this uuid and cancel any running
     * workflow execution with the old uuid.
     */
    @ValueProvider(SetUUIDForRunning.class)
    @ValueReference(ProcessingUuidRef.class)
    String m_lastProcessingUuid = "initialProcessingUuid";

    interface ProcessingUuidRef extends ParameterReference<String> {
    }

    /**
     * This is the trigger for the workflow fetch. It has to be a copy to update the below boolean beforehand.
     */
    @ValueProvider(CopyFromProcessingUuid.class)
    @ValueReference(CopiedProcessingUuidRef.class)
    String m_copiedLastProcessingUuid;

    interface CopiedProcessingUuidRef extends ParameterReference<String> {
    }

    /**
     * When the workflow execution finishes, we copy the processing uuid here to indicate that no workflow is being
     * loaded anymore.
     */
    @ValueReference(FinishedProcessingUuidRef.class)
    @ValueProvider(CopyFromCopiedProcessingUuid.class)
    String m_finishedProcessingUuid = "initialFinishedProcessingUuid";

    interface FinishedProcessingUuidRef extends ParameterReference<String> {
    }

    static final class DoNotPersist implements NodeParametersPersistor<DialogTempState> {

        @Override
        public DialogTempState load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return new DialogTempState();
        }

        @Override
        public void save(final DialogTempState param, final NodeSettingsWO settings) {
            // Do not persist
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }
    }

    static final class SetUUIDForRunning implements StateProvider<String> {

        private Supplier<String> m_currentValueProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
            initializer.computeOnValueChange(WorkflowPathRef.class);
            initializer.computeOnValueChange(WorkflowVersionRef.class);
            initializer.computeOnValueChange(DeploymentIdRef.class);
            m_currentValueProvider = initializer.getValueSupplier(ProcessingUuidRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            final var currentValue = m_currentValueProvider.get();
            WorkflowExecutionConnectorProvider.cancelIfRunningAndRemove(currentValue);
            return UUID.randomUUID().toString();
        }

    }

    static final class CopyFromProcessingUuid implements StateProvider<String> {

        private Supplier<String> m_processingUuidSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_processingUuidSupplier = initializer.computeFromValueSupplier(ProcessingUuidRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) {
            return m_processingUuidSupplier.get();
        }

    }

    static final class CopyFromCopiedProcessingUuid implements StateProvider<String> {

        private Supplier<String> m_processingUuidSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_processingUuidSupplier = initializer.computeFromValueSupplier(CopiedProcessingUuidRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput parametersInput) {
            return m_processingUuidSupplier.get();
        }

    }

    static final class ProvideTrueOnUnfinishedProcessingUuid implements StateProvider<Boolean> {

        private Supplier<String> m_processingUuidSupplier;

        private Supplier<String> m_finishedProcessingUuidSupplier;

        /**
         * We deliberately do not set the same trigger as the one used in {@link WorkflowExecutionConnectorProvider} as
         * trigger here but rather use a copy of the processing uuid there. Otherwise, this would be called in the same
         * thread as the workflow fetching and is blocked until the workflow fetching finishes.
         */
        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_processingUuidSupplier = initializer.computeFromValueSupplier(ProcessingUuidRef.class);
            m_finishedProcessingUuidSupplier = initializer.computeFromValueSupplier(FinishedProcessingUuidRef.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) {
            final var processingUuid = m_processingUuidSupplier.get();
            final var finishedUuid = m_finishedProcessingUuidSupplier.get();
            WorkflowExecutionConnectorProvider.remove(finishedUuid);
            return processingUuid != null && !processingUuid.equals(finishedUuid);
        }

    }
}
