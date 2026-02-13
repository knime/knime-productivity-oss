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
 *   Created on Dec 17, 2025 by paulbaernreuther
 */
package org.knime.workflowservices.knime.caller2;

import java.util.List;
import java.util.function.Supplier;

import org.knime.core.webui.node.dialog.defaultdialog.internal.dirty.DirtyTracker;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersRef;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.InputParameterElement;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.OutputParameterElement;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;

@SuppressWarnings("restriction")
final class MakeDialogDirtyParameters implements NodeParameters {

    @DirtyTracker(WorkflowParametersHaveSameLengthButChangedProvider.class)
    Void m_workflowParametersChanged;

    static final class WorkflowParametersHaveSameLengthButChangedProvider implements StateProvider<Boolean> {
        private Supplier<List<WorkflowParameterElement>> m_initialInputParametersSupplier;

        private Supplier<List<WorkflowParameterElement>> m_initialOutputParametersSupplier;

        private Supplier<List<InputParameterElement>> m_currentInputParametersSupplier;

        private Supplier<List<OutputParameterElement>> m_currentOutputParametersSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_initialInputParametersSupplier = initializer.getValueSupplier(InputParametersInitialValueRef.class);
            m_initialOutputParametersSupplier = initializer.getValueSupplier(OutputParametersInitialValueRef.class);
            m_currentInputParametersSupplier = initializer.computeFromValueSupplier(InputParametersRef.class);
            m_currentOutputParametersSupplier = initializer.computeFromValueSupplier(OutputParametersRef.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            final var initialInputParameters = m_initialInputParametersSupplier.get();
            final var initialOutputParameters = m_initialOutputParametersSupplier.get();
            final var currentInputParameters = m_currentInputParametersSupplier.get();
            final var currentOutputParameters = m_currentOutputParametersSupplier.get();
            if (initialInputParameters.size() != currentInputParameters.size()
                || initialOutputParameters.size() != currentOutputParameters.size()) {
                return false;
            }
            return !initialInputParameters.equals(currentInputParameters)
                || !initialOutputParameters.equals(currentOutputParameters);
        }
    }
}
