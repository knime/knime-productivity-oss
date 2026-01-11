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
package org.knime.workflowservices.json.table.caller2;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Before;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.Migrate;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowLayout;
import org.knime.workflowservices.CallWorkflowParameters;
import org.knime.workflowservices.CallWorkflowParameters.WithError;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowFetchIsRunning;
import org.knime.workflowservices.ErrorMessageProvider;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.ReportingParameters;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.json.table.caller.CallWorkflowTableNodeConfiguration;
import org.knime.workflowservices.json.table.caller2.CalleeParameters.DialogState.DoNotPersistDialogState;
import org.knime.workflowservices.json.table.caller2.ParameterIdsUtil.ParameterId;

import jakarta.json.JsonValue;

@SuppressWarnings("restriction")
final class CalleeParameters implements NodeParameters {

    @After(CallWorkflowLayout.WorkflowOrDeploymentSection.class)
    interface LoadingAndErrorMessages {
    }

    @After(LoadingAndErrorMessages.class)
    @Section(title = "Parameters")
    @Effect(predicate = WorkflowNodesNotReady.class, type = Effect.EffectType.HIDE)
    @Before(ReportingParameters.ReportingSection.class)
    interface ContainerParametersSection {
    }

    @TextMessage(LoadingWorkflowNodesMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.SHOW)
    Void m_loadingInputNodesMessage;

    @TextMessage(WorkflowNodesErrorMessageProvider.class)
    @Layout(LoadingAndErrorMessages.class)
    @Effect(predicate = WorkflowFetchIsRunning.class, type = Effect.EffectType.HIDE)
    Void m_inputNodesErrorMessage;

    @Widget(title = "Read input table from",
        description = "The data table provided at the input of the <i>Call Workflow (Table Based)</i> node can be"
            + " sent to a <i>Container Input (Table)</i> node in the called workflow via the input parameter"
            + " defined in the <i>Container Input (Table)</i> node.")
    @ChoicesProvider(TableInputParameterChoicesProvider.class)
    @Persist(configKey = CallWorkflowTableNodeConfiguration.SELECTED_INPUT_PARAMETER_CFG_KEY)
    @ValueProvider(DialogState.InputParameterUpdater.class)
    @ValueReference(InputParameterRef.class)
    @Layout(ContainerParametersSection.class)
    @Migrate(loadDefaultIfAbsent = true)
    String m_inputTableParameter = "";

    interface InputParameterRef extends ParameterReference<String> {
    }

    @Widget(title = "Fill output table from",
        description = "After the execution of the called workflow, a data table can be received from a"
            + " <i>Container Output (Table)</i> node in the called workflow via the output parameter defined in"
            + " the <i>Container Output (Table)</i> node. The received data table will be made available at the"
            + " output port of the <i>Call Workflow (Table Based)</i> node.")
    @ChoicesProvider(OutputParameterChoicesProvider.class)
    @Persist(configKey = CallWorkflowTableNodeConfiguration.SELECTED_OUTPUT_PARAMETER_CFG_KEY)
    @ValueProvider(DialogState.OutputParameterUpdater.class)
    @ValueReference(OutputParameterRef.class)
    @Layout(ContainerParametersSection.class)
    @Migrate(loadDefaultIfAbsent = true)
    String m_outputTableParameter = "";

    interface OutputParameterRef extends ParameterReference<String> {
    }

    @Widget(title = "Push flow variables to",
        description = "Certain flow variables available in the <i>Call Workflow (Table Based)</i> node can be sent"
            + " to a <i>Container Input (Variable)</i> node in the called workflow. Currently, neither boolean,"
            + " long, nor list or set flow variables are supported and will be ignored without warning.")
    @ChoicesProvider(FlowVariableInputParameterChoicesProvider.class)
    @Persist(configKey = CallWorkflowTableNodeConfiguration.FLOW_VARIABLE_DESTINATION_CFG_KEY)
    @ValueProvider(DialogState.FlowVariableParameterUpdater.class)
    @ValueReference(FlowVariableParameterRef.class)
    @Layout(ContainerParametersSection.class)
    @Migrate(loadDefaultIfAbsent = true)
    String m_flowVariableParameter = "";

    interface FlowVariableParameterRef extends ParameterReference<String> {
    }

    @Widget(title = "Push flow credentials to",
        description = "All flow credentials available in the <i>Call Workflow (Table Based)</i> node can be sent to"
            + " a <i>Container Input (Credential)</i> node in the called workflow via the credential-input"
            + " defined in the <i>Container Input (Credential)</i> node")
    @ChoicesProvider(CredentialsInputParameterChoicesProvider.class)
    @Persist(configKey = CallWorkflowTableNodeConfiguration.FLOW_CREDENTIALS_DESTINATION_CFG_KEY)
    @ValueProvider(DialogState.CredentialsParameterUpdater.class)
    @ValueReference(CredentialsParameterRef.class)
    @Layout(ContainerParametersSection.class)
    @Migrate(loadDefaultIfAbsent = true)
    String m_credentialsParameter = "";

    interface CredentialsParameterRef extends ParameterReference<String> {
    }

    @Widget(title = "Use fully qualified names for parameters",
        description = "If checked, use the full parameter name to select the input and output. The parameter names are"
            + " defined by the user in the configuration dialog of each of the parameters (like Container Input"
            + " (Table) and Container Output (Table)node), e.g. \"timeout\", \"count\", etc. Using these 'simple names'"
            + " may lead to duplicates, hence a 'fully qualified name' is constructed by appending a suffix, e.g."
            + " \"timeout-1\" or \"count-5:6\". While these suffixes guarantee unique parameter names across one"
            + " workflow they may cause names not to match in case you call out to different workflows (e.g. in a"
            + " loop). It's good practice to (manually) ensure that parameter names in the called workflow are unique"
            + " and hence often the 'simple name' is sufficient (= do not check the box).<br/><br/>"
            + "In the case a parameter defined in any of the <i>Container Input (Table)</i>, <i>Container Output"
            + " (Table)</i> or <i>Container Input (Variable)</i> nodes is not unique, the node ID will be appended to"
            + " the parameter even though the use fully qualified names check box has not been checked.")
    @ValueReference(UseFullyQualifiedIdsRef.class)
    @Persist(configKey = CallWorkflowTableNodeConfiguration.USE_FULLY_QUALIFIED_IDS_CFG_KEY)
    @Layout(ContainerParametersSection.class)
    boolean m_useFullyQualifiedIds;

    interface UseFullyQualifiedIdsRef extends ParameterReference<Boolean> {
    }

    @Persistor(DoNotPersistDialogState.class)
    DialogState m_dialogState = new DialogState();

    static final class DialogState implements NodeParameters {

        @ValueReference(InputParameterChoicesRef.class)
        @ValueProvider(TableInputParameterChoicesValueProvider.class)
        ParameterId[] m_inputParameterChoices = new ParameterId[0];

        interface InputParameterChoicesRef extends ParameterReference<ParameterId[]> {
        }

        @ValueReference(OutputParameterChoicesRef.class)
        @ValueProvider(OutputParameterChoicesValueProvider.class)
        ParameterId[] m_outputParameterChoices = new ParameterId[0];

        interface OutputParameterChoicesRef extends ParameterReference<ParameterId[]> {
        }

        @ValueReference(FlowVariableParameterChoicesRef.class)
        @ValueProvider(FlowVariableInputParameterChoicesValueProvider.class)
        ParameterId[] m_flowVariableParameterChoices = new ParameterId[0];

        interface FlowVariableParameterChoicesRef extends ParameterReference<ParameterId[]> {
        }

        @ValueReference(CredentialsParameterChoicesRef.class)
        @ValueProvider(CredentialsInputParameterChoicesValueProvider.class)
        ParameterId[] m_credentialsParameterChoices = new ParameterId[0];

        interface CredentialsParameterChoicesRef extends ParameterReference<ParameterId[]> {
        }

        static final class DoNotPersistDialogState implements NodeParametersPersistor<DialogState> {
            @Override
            public DialogState load(final NodeSettingsRO settings) throws InvalidSettingsException {
                return new DialogState();
            }

            @Override
            public void save(final DialogState param, final NodeSettingsWO settings) {
                // do nothing
            }

            @Override
            public String[][] getConfigPaths() {
                return new String[0][];
            }

        }

        abstract static class ParameterUpdater implements StateProvider<String> {

            private final Class<? extends ParameterReference<ParameterId[]>> m_parameterChoicesRefClass;

            private final Class<? extends ParameterReference<String>> m_selectedParameterIdRefClass;

            ParameterUpdater(final Class<? extends ParameterReference<ParameterId[]>> parameterChoicesRefClass,
                final Class<? extends ParameterReference<String>> selectedParameterIdRefClass) {
                m_parameterChoicesRefClass = parameterChoicesRefClass;
                m_selectedParameterIdRefClass = selectedParameterIdRefClass;
            }

            Supplier<ParameterId[]> m_parameterIdsSupplier;

            Supplier<String> m_selectedParameterIdSupplier;

            Supplier<Boolean> m_useFullyQualifiedIdsSupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                m_parameterIdsSupplier = initializer.computeFromValueSupplier(m_parameterChoicesRefClass);
                m_selectedParameterIdSupplier = initializer.getValueSupplier(m_selectedParameterIdRefClass);
                m_useFullyQualifiedIdsSupplier = initializer.computeFromValueSupplier(UseFullyQualifiedIdsRef.class);
            }

            @Override
            public String computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {

                final var parameterIds = m_parameterIdsSupplier.get();
                final var selectedParameterId = m_selectedParameterIdSupplier.get();
                if (parameterIds.length == 0) {
                    if (isEmpty(selectedParameterId)) {
                        throw new StateComputationFailureException();
                    }
                    /**
                     * Make empty to preserve valid parameters.
                     */
                    return "";
                }
                final var useFullyQualifiedIds = m_useFullyQualifiedIdsSupplier.get();
                if (isEmpty(selectedParameterId)) {
                    return parameterIds[0].getId(useFullyQualifiedIds);
                }
                final var found = Arrays.stream(parameterIds)
                    .anyMatch(pid -> pid.getId(useFullyQualifiedIds).equals(selectedParameterId));
                if (found) {
                    throw new StateComputationFailureException();
                }
                final var foundWithOtherIdType = Arrays.stream(parameterIds)
                    .filter(pid -> pid.getId(!useFullyQualifiedIds).equals(selectedParameterId)).findFirst();
                if (foundWithOtherIdType.isPresent()) {
                    return foundWithOtherIdType.get().getId(useFullyQualifiedIds);
                }
                throw new StateComputationFailureException();
            }

            private static boolean isEmpty(final String s) {
                return s == null || s.isEmpty();
            }

        }

        static final class InputParameterUpdater extends ParameterUpdater {
            InputParameterUpdater() {
                super(InputParameterChoicesRef.class, InputParameterRef.class);
            }
        }

        static final class OutputParameterUpdater extends ParameterUpdater {
            OutputParameterUpdater() {
                super(OutputParameterChoicesRef.class, OutputParameterRef.class);
            }
        }

        static final class FlowVariableParameterUpdater extends ParameterUpdater {
            FlowVariableParameterUpdater() {
                super(FlowVariableParameterChoicesRef.class, FlowVariableParameterRef.class);
            }
        }

        static final class CredentialsParameterUpdater extends ParameterUpdater {
            CredentialsParameterUpdater() {
                super(CredentialsParameterChoicesRef.class, CredentialsParameterRef.class);
            }
        }
    }

    static final class WorkflowInputOutputNodesPairProvider implements StateProvider< //
            CallWorkflowParameters.WithError<Pair<Map<String, JsonValue>, Map<String, JsonValue>>, Exception>> {

        private Supplier< //
                CallWorkflowParameters.WithError<WorkflowExecutionConnector, Exception>> m_workflowConnectionProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowConnectionProvider =
                initializer.computeFromProvidedState(CallWorkflowParameters.WorkflowExecutionConnectorProvider.class);
        }

        @Override
        public CallWorkflowParameters.WithError<Pair<Map<String, JsonValue>, Map<String, JsonValue>>, Exception>
            computeState(final NodeParametersInput parametersInput) {
            final var connectionOrError = m_workflowConnectionProvider.get();

            if (connectionOrError.hasError()) {
                return new CallWorkflowParameters.WithError<>(connectionOrError.exception());
            }

            try (var wfBackend = connectionOrError.value().createWorkflowBackend()) {
                final var inputNodes = getInputNodeValues(wfBackend);
                final var outputNodes = wfBackend.getOutputValuesForConfiguration();
                return new CallWorkflowParameters.WithError<>(new Pair<>(inputNodes, outputNodes));
            } catch (Exception e) {
                return new CallWorkflowParameters.WithError<>(e);
            }
        }

        private static Map<String, JsonValue> getInputNodeValues(final IWorkflowBackend backend) {
            Map<String, JsonValue> inputNodes = new HashMap<>();
            for (Map.Entry<String, ExternalNodeData> e : backend.getInputNodes().entrySet()) {
                var json = e.getValue().getJSONValue();
                if (json != null) {
                    inputNodes.put(e.getKey(), json);
                }
            }
            return inputNodes;
        }
    }

    static final class InputParameterKeysProvider implements StateProvider<ParameterIdsUtil.InputParameterKeys> {

        private Supplier< //
                WithError<Pair<Map<String, JsonValue>, Map<String, JsonValue>>, Exception>> m_inputNodesProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputNodesProvider = initializer.computeFromProvidedState(WorkflowInputOutputNodesPairProvider.class);
        }

        @Override
        public ParameterIdsUtil.InputParameterKeys computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var inputNodesOrError = m_inputNodesProvider.get();
            if (inputNodesOrError.hasError()) {
                throw new StateComputationFailureException();
            }
            final var inputNodes = inputNodesOrError.value().getFirst();
            return ParameterIdsUtil.getInputParameterKeys(inputNodes);
        }
    }

    abstract static class InputParameterChoicesProviderBase implements StateProvider<ParameterId[]> {

        protected abstract Collection<String>
            getInputParameterKeys(ParameterIdsUtil.InputParameterKeys inputParameterKeys);

        private Supplier<ParameterIdsUtil.InputParameterKeys> m_inputParameterKeysProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputParameterKeysProvider = initializer.computeFromProvidedState(InputParameterKeysProvider.class);
        }

        @Override
        public ParameterId[] computeState(final NodeParametersInput parametersInput) {
            final var inputParameterKeys = m_inputParameterKeysProvider.get();
            final var keys = getInputParameterKeys(inputParameterKeys);
            return ParameterIdsUtil.getParameterIdsArray(keys);
        }
    }

    static final class TableInputParameterChoicesValueProvider extends InputParameterChoicesProviderBase {

        @Override
        protected Collection<String>
            getInputParameterKeys(final ParameterIdsUtil.InputParameterKeys inputParameterKeys) {
            return inputParameterKeys.tableInputKeys();
        }
    }

    static final class FlowVariableInputParameterChoicesValueProvider extends InputParameterChoicesProviderBase {

        @Override
        protected Collection<String>
            getInputParameterKeys(final ParameterIdsUtil.InputParameterKeys inputParameterKeys) {
            return inputParameterKeys.flowVariableInputKeys();
        }
    }

    static final class CredentialsInputParameterChoicesValueProvider extends InputParameterChoicesProviderBase {

        @Override
        protected Collection<String>
            getInputParameterKeys(final ParameterIdsUtil.InputParameterKeys inputParameterKeys) {
            return inputParameterKeys.credentialVariableInputKeys();
        }
    }

    static final class OutputParameterChoicesValueProvider implements StateProvider<ParameterId[]> {

        private Supplier< //
                WithError<Pair<Map<String, JsonValue>, Map<String, JsonValue>>, Exception>> m_inputNodesProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_inputNodesProvider = initializer.computeFromProvidedState(WorkflowInputOutputNodesPairProvider.class);
        }

        @Override
        public ParameterId[] computeState(final NodeParametersInput parametersInput) {
            final var inputNodesOrError = m_inputNodesProvider.get();
            if (inputNodesOrError.hasError()) {
                return new ParameterId[0];
            }
            final var outputNodes = inputNodesOrError.value().getSecond();
            final var keys = outputNodes.keySet();
            return ParameterIdsUtil.getParameterIdsArray(keys);
        }

    }

    abstract static class ParameterChoicesProviderBase implements StringChoicesProvider {

        private final Class<? extends ParameterReference<ParameterId[]>> m_parameterChoicesRefClass;

        private Supplier<ParameterId[]> m_parameterIdsSupplier;

        private Supplier<Boolean> m_useFullyQualifiedIdsSupplier;

        ParameterChoicesProviderBase(
            final Class<? extends ParameterReference<ParameterId[]>> parameterChoicesRefClass) {
            m_parameterChoicesRefClass = parameterChoicesRefClass;
        }

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_parameterIdsSupplier = initializer.computeFromValueSupplier(m_parameterChoicesRefClass);
            m_useFullyQualifiedIdsSupplier = initializer.computeFromValueSupplier(UseFullyQualifiedIdsRef.class);

        }

        @Override
        public List<String> choices(final NodeParametersInput context) {
            final var useFullyQualifiedIds = m_useFullyQualifiedIdsSupplier.get();
            return Arrays.stream(m_parameterIdsSupplier.get()).map(id -> id.getId(useFullyQualifiedIds)).toList();
        }

    }

    static final class TableInputParameterChoicesProvider extends ParameterChoicesProviderBase {
        TableInputParameterChoicesProvider() {
            super(DialogState.InputParameterChoicesRef.class);
        }
    }

    static final class FlowVariableInputParameterChoicesProvider extends ParameterChoicesProviderBase {
        FlowVariableInputParameterChoicesProvider() {
            super(DialogState.FlowVariableParameterChoicesRef.class);
        }
    }

    static final class CredentialsInputParameterChoicesProvider extends ParameterChoicesProviderBase {
        CredentialsInputParameterChoicesProvider() {
            super(DialogState.CredentialsParameterChoicesRef.class);
        }
    }

    static final class OutputParameterChoicesProvider extends ParameterChoicesProviderBase {
        OutputParameterChoicesProvider() {
            super(DialogState.OutputParameterChoicesRef.class);
        }
    }

    /**
     * Always present but sometimes hidden message indicating that input nodes are being loaded.
     */
    static final class LoadingWorkflowNodesMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

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
    static final class WorkflowNodesErrorMessageProvider
        extends ErrorMessageProvider<Pair<Map<String, JsonValue>, Map<String, JsonValue>>> {

        WorkflowNodesErrorMessageProvider() {
            super("Failed to load workflow parameters", WorkflowInputOutputNodesPairProvider.class);
        }

    }

    static final class HasWorkflowNodesErrorMessage implements StateProvider<Boolean>, BooleanReference {

        private Supplier<Optional<TextMessage.Message>> m_errorMessageProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_errorMessageProvider = initializer.computeFromProvidedState(WorkflowNodesErrorMessageProvider.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) {
            return m_errorMessageProvider.get().isPresent();
        }
    }

    static final class WorkflowNodesNotReady implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return or(i.getPredicate(WorkflowFetchIsRunning.class), i.getPredicate(HasWorkflowNodesErrorMessage.class));
        }
    }

}
