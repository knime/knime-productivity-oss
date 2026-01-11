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
 *   Created on Jan 9, 2026 by paulbaernreuther
 */
package org.knime.workflowservices.json.row.caller3;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.internal.StateProviderInitializerInternal;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowLayout;
import org.knime.workflowservices.CallWorkflowParameters;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowFetchIsRunning;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.ReportingParameters;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;

@SuppressWarnings("restriction")
final class CalleeParameters implements NodeParameters {

    @After(CallWorkflowLayout.WorkflowOrDeploymentSection.class)
    interface LoadingAndErrorMessages {
    }

    @After(LoadingAndErrorMessages.class)
    @Section(title = "Input Parameters")
    @Effect(predicate = InputNodesNotReady.class, type = Effect.EffectType.HIDE)
    @Before(ReportingParameters.ReportingSection.class)
    interface ContainerInputParametersSection {
    }

    @TextMessage(LoadingInputNodesMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.SHOW)
    Void m_loadingInputNodesMessage;

    @TextMessage(InputNodesErrorMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.HIDE)
    Void m_inputNodesErrorMessage;

    @Widget(title = "Input parameters",
        description = "After you have selected a workflow, you will see the input parameters of the workflow. "
            + "Configure how to pass in data for each parameter.")
    @ArrayWidget(showSortButtons = false, hasFixedSize = true, elementTitle = "Input parameter")
    @ValueProvider(ContainerInputParametersProvider.class)
    @Effect(predicate = InputNodesNotReady.class, type = Effect.EffectType.HIDE)
    @ValueReference(ContainerInputParametersRef.class)
    @Layout(ContainerInputParametersSection.class)
    ContainerInputParameters[] m_containerInputParameters = new ContainerInputParameters[0];

    @TextMessage(NoInputParametersMessage.class)
    @Effect(predicate = InputNodesNotReady.class, type = Effect.EffectType.HIDE)
    @Layout(ContainerInputParametersSection.class)
    Void m_noInputParametersMessage;

    static final class NoInputParametersMessage implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<ContainerInputParameters[]> m_inputParametersSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputParametersSupplier = initializer.computeFromProvidedState(ContainerInputParametersProvider.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var inputParameters = m_inputParametersSupplier.get();
            if (inputParameters.length == 0) {
                return Optional.of(new TextMessage.Message("No input parameters",
                    "The selected worklfow has no input parameters.", TextMessage.MessageType.INFO));
            }
            return Optional.empty();

        }

    }

    /**
     * Whether to change the keys of the input data map sent to the callee workflow. If true, node IDs will be removed
     * from parameter names (e.g., string-input instead of string-input-6).
     *
     * This is added only for backward compatibility and is not exposed via the node dialog. With the switch to this
     * webui dialog it is also no longer configurable via the flow variable in the dialog.
     */
    boolean m_dropParameterIdentifiers;

    interface ContainerInputParametersRef extends ParameterReference<ContainerInputParameters[]> {
    }

    @ValueReference(ContainerInputParametersInitialValueRef.class)
    @ValueProvider(InitialContainerInputParametersProvider.class)
    @Persistor(DoNotPersistArray.class)
    ContainerInputParameters[] m_initialValueContainerInputParameters = new ContainerInputParameters[0];

    interface ContainerInputParametersInitialValueRef extends ParameterReference<ContainerInputParameters[]> {
    }

    static final class DoNotPersistArray implements NodeParametersPersistor<ContainerInputParameters[]> {

        @Override
        public ContainerInputParameters[] load(final NodeSettingsRO settings) throws InvalidSettingsException {
            return new ContainerInputParameters[0];
        }

        @Override
        public void save(final ContainerInputParameters[] param, final NodeSettingsWO settings) {
            // Do not persist
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[0][];
        }
    }

    static ContainerInputParameters[] fromWorkflowBackend(final IWorkflowBackend backend) {
        final var inputNodes = backend.getInputNodes();
        return inputNodes.entrySet().stream().map(e -> new ContainerInputParameters(e.getKey(), e.getValue()))
            .toArray(ContainerInputParameters[]::new);
    }

    static final class WorkflowInputNodesProvider
        implements StateProvider<CallWorkflowParameters.WithError<Map<String, ExternalNodeData>, Exception>> {

        private Supplier<CallWorkflowParameters.WithError<WorkflowExecutionConnector, Exception>> m_workflowConnectionProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowConnectionProvider =
                initializer.computeFromProvidedState(CallWorkflowParameters.WorkflowExecutionConnectorProvider.class);
        }

        @Override
        public CallWorkflowParameters.WithError<Map<String, ExternalNodeData>, Exception>
            computeState(final NodeParametersInput parametersInput) {
            final var connectionOrError = m_workflowConnectionProvider.get();

            if (connectionOrError.hasError()) {
                return new CallWorkflowParameters.WithError<>(connectionOrError.exception());
            }

            try (var wfBackend = connectionOrError.value().createWorkflowBackend()) {
                final var inputNodes = wfBackend.getInputNodes();
                return new CallWorkflowParameters.WithError<>(inputNodes);
            } catch (Exception e) {
                return new CallWorkflowParameters.WithError<>(e);
            }
        }
    }

    /**
     * Always present but sometimes hidden message indicating that input nodes are being loaded.
     */
    static final class LoadingInputNodesMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            return Optional.of(new TextMessage.Message("Loading workflow input parameters...",
                "The input parameters are being loaded from the selected workflow.", TextMessage.MessageType.INFO));
        }
    }

    /**
     * Error message shown when there is an error loading input nodes.
     */
    static final class InputNodesErrorMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<CallWorkflowParameters.WithError<Map<String, ExternalNodeData>, Exception>> m_inputNodesProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputNodesProvider = initializer.computeFromProvidedState(WorkflowInputNodesProvider.class);
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var inputNodesOrError = m_inputNodesProvider.get();
            if (inputNodesOrError.hasError()) {
                final var exception = inputNodesOrError.exception();
                return Optional.of(new TextMessage.Message("Failed to load workflow input parameters",
                    toReadableString(exception), TextMessage.MessageType.ERROR));
            }
            return Optional.empty();
        }

        static String toReadableString(final Exception exception) {
            final var rootMessage = getRootMessage(exception);
            if (rootMessage.isEmpty() || exception instanceof IllegalStateException) {
                return rootMessage;
            }
            return exception.getClass().getSimpleName() + ": " + rootMessage;
        }

        static String getRootMessage(final Throwable throwable) {
            Throwable root = throwable;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            final var rootMessage = root.getMessage();
            return rootMessage == null ? "" : rootMessage;
        }
    }

    static final class HasInputNodesErrorMessage implements StateProvider<Boolean>, BooleanReference {

        private Supplier<Optional<TextMessage.Message>> m_errorMessageProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_errorMessageProvider = initializer.computeFromProvidedState(InputNodesErrorMessageProvider.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) {
            return m_errorMessageProvider.get().isPresent();
        }
    }

    static final class InputNodesNotReady implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return or(i.getPredicate(WorkflowFetchIsRunning.class), i.getPredicate(HasInputNodesErrorMessage.class));
        }
    }

    /**
     * Captures the initial value of container input parameters for merging with new workflow parameters.
     */
    static final class InitialContainerInputParametersProvider implements StateProvider<ContainerInputParameters[]> {

        private Supplier<ContainerInputParameters[]> m_initialValueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_initialValueSupplier = initializer.getValueSupplier(ContainerInputParametersRef.class);
            ((StateProviderInitializerInternal)initializer).computeAfterApplyDialog();
        }

        @Override
        public ContainerInputParameters[] computeState(final NodeParametersInput parametersInput) {
            return m_initialValueSupplier.get();
        }
    }

    /**
     * ValueProvider for the container input parameters array. Merges new workflow parameters with existing
     * configurations to preserve user selections when parameter names match.
     */
    static final class ContainerInputParametersProvider implements StateProvider<ContainerInputParameters[]> {

        private Supplier<CallWorkflowParameters.WithError<Map<String, ExternalNodeData>, Exception>> m_inputNodesProvider;

        private Supplier<ContainerInputParameters[]> m_existingParametersProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputNodesProvider = initializer.computeFromProvidedState(WorkflowInputNodesProvider.class);
            m_existingParametersProvider = initializer.getValueSupplier(ContainerInputParametersInitialValueRef.class);
        }

        @Override
        public ContainerInputParameters[] computeState(final NodeParametersInput parametersInput) {
            final var inputNodesOrError = m_inputNodesProvider.get();
            if (inputNodesOrError.hasError()) {
                return new ContainerInputParameters[0];
            }

            final var inputNodes = inputNodesOrError.value();
            return mergeWithExisting(inputNodes, m_existingParametersProvider.get());
        }

        /**
         * Merge new workflow input nodes with existing parameter configurations. For each input node from the workflow,
         * use the existing configuration if available, otherwise create a new one.
         */
        private static ContainerInputParameters[] mergeWithExisting(final Map<String, ExternalNodeData> inputNodes,
            final ContainerInputParameters[] existingParameters) {
            final var existingParamsMap = Arrays.stream(existingParameters)
                .collect(Collectors.toMap(p -> p.m_parameterName, p -> p, (a, b) -> a));
            return inputNodes.entrySet().stream().map(entry -> {
                final var parameterName = entry.getKey();
                final var existing = Optional.ofNullable(existingParamsMap.get(parameterName));
                return existing.orElseGet(() -> new ContainerInputParameters(parameterName, entry.getValue()));
            }).toArray(ContainerInputParameters[]::new);
        }
    }
}
