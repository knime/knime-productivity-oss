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
 *   Created on Dec 16, 2025 by paulbaernreuther
 */
package org.knime.workflowservices.knime.caller2;

import java.util.List;
import java.util.Objects;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.ArrayWidgetInternal;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowLayout;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowFetchIsRunning;
import org.knime.workflowservices.knime.caller.WorkflowParameters;
import org.knime.workflowservices.knime.caller2.CalleeParameters.CalleeParametersPersistor;
import org.knime.workflowservices.knime.caller2.ErrorAndLoadingUtil.LoadingParametersMessageProvider;
import org.knime.workflowservices.knime.caller2.ErrorAndLoadingUtil.ParametersNotReady;
import org.knime.workflowservices.knime.caller2.ErrorAndLoadingUtil.WorkflowParametersErrorMessageProvider;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.AdditionalInputPortsToBeRemovedMessageProvider;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.InitialInputParametersProvider;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.InputParameterElement;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.InputParametersProvider;
import org.knime.workflowservices.knime.caller2.InputParametersUtil.NoInputParameters;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.AdditionalOutputPortsToBeRemovedMessageProvider;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.InitialOutputParametersProvider;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.NoOutputParameters;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.OutputParameterElement;
import org.knime.workflowservices.knime.caller2.OutputParametersUtil.OutputParametersProvider;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;

@Persistor(CalleeParametersPersistor.class)
@SuppressWarnings("restriction")
final class CalleeParameters implements NodeParameters {

    private static final String INPUT_PARAMS_DESC = """
            After you have specified a workflow path, you will see the input parameters of the workflow. \
            Every input parameter corresponds to an input port \
            of the node. This section shows which input port's data is sent  \
            to which input parameter. By using the arrows, you can \
            configure the binding of input ports to input parameters.\
                """;

    private static final String OUTPUT_PARAMS_DESC =
        "Similar to the input parameters. Maps data sent back from the workflow to this node's output ports.";

    @After(CallWorkflowLayout.WorkflowOrDeploymentSection.class)
    interface LoadingAndErrorMessages {
    }

    @Section(title = "Input Parameters", description = INPUT_PARAMS_DESC)
    @After(LoadingAndErrorMessages.class)
    @Effect(predicate = ParametersNotReady.class, type = Effect.EffectType.HIDE)
    interface InputParametersSection {
    }

    @After(InputParametersSection.class)
    @Before(CallWorkflowLayout.TimeoutsSection.class)
    @Section(title = "Output Parameters", description = OUTPUT_PARAMS_DESC)
    @Effect(predicate = ParametersNotReady.class, type = Effect.EffectType.HIDE)
    interface OutputParametersSection {
    }

    @TextMessage(LoadingParametersMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.SHOW)
    Void m_loadingInputParametersMessage;

    @TextMessage(WorkflowParametersErrorMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.HIDE)
    Void m_workflowParametersErrorMessage;

    @Layout(LoadingAndErrorMessages.class)
    MakeDialogDirtyParameters m_makeDialogDirtyParameters = new MakeDialogDirtyParameters();

    @Widget(title = "Input parameters", description = INPUT_PARAMS_DESC)
    @ArrayWidget(hasFixedSize = true, showSortButtons = true, elementTitle = "Input port")
    @ArrayWidgetInternal(subTitleProvider = InputParameterElement.InputPortTypeSubTitleProvider.class)
    @ValueProvider(InputParametersProvider.class)
    @ValueReference(InputParametersRef.class)
    @Layout(InputParametersSection.class)
    @Effect(predicate = NoInputParameters.class, type = Effect.EffectType.HIDE)
    List<InputParameterElement> m_inputParameters = List.of();

    static final class InputParametersRef implements ParameterReference<List<InputParameterElement>> {
    }

    @ValueReference(InputParametersInitialValueRef.class)
    @ValueProvider(InitialInputParametersProvider.class)
    List<WorkflowParameterElement> m_initialValueInputParameters = List.of();

    interface InputParametersInitialValueRef extends ParameterReference<List<WorkflowParameterElement>> {
    }

    @TextMessage(AdditionalInputPortsToBeRemovedMessageProvider.class)
    @Layout(InputParametersSection.class)
    Void m_additionalInputPortsToBeRemovedMessage;

    @Widget(title = "Output parameters", description = OUTPUT_PARAMS_DESC)
    @ArrayWidget(hasFixedSize = true, showSortButtons = true, elementTitle = "Output port")
    @ArrayWidgetInternal(subTitleProvider = OutputParameterElement.OutputPortTypeSubTitleProvider.class)
    @ValueProvider(OutputParametersProvider.class)
    @ValueReference(OutputParametersRef.class)
    @Layout(OutputParametersSection.class)
    @Effect(predicate = NoOutputParameters.class, type = Effect.EffectType.HIDE)
    List<OutputParameterElement> m_outputParameters = List.of();

    static final class OutputParametersRef implements ParameterReference<List<OutputParameterElement>> {
    }

    @ValueReference(OutputParametersInitialValueRef.class)
    @ValueProvider(InitialOutputParametersProvider.class)
    List<WorkflowParameterElement> m_initialValueOutputParameters = List.of();

    interface OutputParametersInitialValueRef extends ParameterReference<List<WorkflowParameterElement>> {
    }

    @TextMessage(AdditionalOutputPortsToBeRemovedMessageProvider.class)
    @Layout(OutputParametersSection.class)
    Void m_additionalOutPortsToBeRemovedMessage;

    static final class CalleeParametersPersistor implements NodeParametersPersistor<CalleeParameters> {

        @Override
        public CalleeParameters load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var workflowParameters = WorkflowParameters.loadFrom(settings);
            return new CalleeParameters(workflowParameters);
        }

        @Override
        public void save(final CalleeParameters param, final NodeSettingsWO settings) {
            param.toWorkflowParameters().saveTo(settings);
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }

    }

    private WorkflowParameters toWorkflowParameters() {
        final var inputParams = m_inputParameters.stream().map(WorkflowParameterElement::toWorkflowParameter)
            .filter(Objects::nonNull).toList();
        final var outputParams = m_outputParameters.stream().map(WorkflowParameterElement::toWorkflowParameter)
            .filter(Objects::nonNull).toList();
        return new WorkflowParameters(inputParams, outputParams);
    }

    CalleeParameters() {
        // default constructor
    }

    private CalleeParameters(final WorkflowParameters workflowParameters) {
        m_inputParameters =
            workflowParameters.getInputParameters().stream().map(p -> new InputParameterElement(p.getParameterName(),
                WorkflowParameterElement.getPortTypeId(p.getPortType()))).toList();
        m_outputParameters =
            workflowParameters.getOutputParameters().stream().map(p -> new OutputParameterElement(p.getParameterName(),
                WorkflowParameterElement.getPortTypeId(p.getPortType()))).toList();
    }

}
