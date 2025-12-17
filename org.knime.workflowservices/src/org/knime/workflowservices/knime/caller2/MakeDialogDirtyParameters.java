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

import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.WidgetInternal;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.booleanhelpers.DoNotPersistBoolean;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersRef;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.InputParameterElement;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.OutputParameterElement;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;

/**
 * Workaround using a checkbox to make the dialog dirty even if none of the controls in the dialog changed. It is
 * similar to workarounds used in other nodes to achieve the same (e.g. the "Visualization Property Extractor") except
 * that it does not use a third boolean that tracks whether it is initialized (which was only needed to prevent a very
 * slight flickering anyway) since in our scenario this checkbox is not necessarily only appearing initially but
 * whenever the condition that updates it becomes true.
 *
 * There is one edge-case that is still not covered: If the dialog is applied with parameters that do not force a
 * rerender of the dialog and this checkbox was checked, then the checkbox becomes hidden but now also when one changes
 * the order of the parameters, the checkbox appears again but does not make the dialog dirty anymore since its last
 * applied state was "true" already. We accept this small limitation for now and will get rid of this checkbox in the
 * future anyway.
 */
@SuppressWarnings("restriction")
final class MakeDialogDirtyParameters implements NodeParameters {

    @ValueReference(WorkflowParametersChangedCopyRef.class)
    @ValueProvider(WorkflowParametersHaveSameLengthButChangedProvider.class)
    boolean m_showWorkflowParametersChanged;

    static final class WorkflowParametersChangedCopyRef implements BooleanReference {
    }

    @Widget(title = "Change on apply",
        description = "This option is automatically set when input or output parameters changed but are the same "
            + "number as the existing ports in order to allow applying the dialog in this case."
            + " Manual changes to this setting are ignored.")
    @WidgetInternal(hideControlInNodeDescription = "This is a helper setting to make the dialog dirty.")
    @Persistor(DoNotPersistBoolean.class)
    @Effect(predicate = WorkflowParametersChangedCopyRef.class, type = EffectType.SHOW)
    @ValueProvider(DirtyCheckboxValueSupplier.class)
    @ValueReference(WorkflowParametersChangedRef.class)
    boolean m_workflowParametersChanged;

    static final class WorkflowParametersChangedRef implements BooleanReference {
    }

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

    static final class DirtyCheckboxValueSupplier implements StateProvider<Boolean> {

        private Supplier<Boolean> m_workflowParametersChangedSupplier;

        private Supplier<Boolean> m_targetValueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_targetValueSupplier =
                initializer.computeFromProvidedState(WorkflowParametersHaveSameLengthButChangedProvider.class);
            m_workflowParametersChangedSupplier =
                initializer.computeFromValueSupplier(WorkflowParametersChangedRef.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            final var targetValue = m_targetValueSupplier.get();
            if (m_workflowParametersChangedSupplier.get().equals(targetValue)) {
                throw new StateComputationFailureException();
            }
            return targetValue;
        }

    }
}
