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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.internal.StateProviderInitializerInternal;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowParameters;
import org.knime.workflowservices.IWorkflowBackend.ResourceContentType;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.knime.caller.WorkflowParameter;

@SuppressWarnings("restriction")
final class WorkflowParametersUtil {

    private WorkflowParametersUtil() {
        // prevent instantiation
    }

    /**
     * Intermediate state provider that loads both input and output resource descriptions from the workflow backend in a
     * single try-with-resources block.
     */
    static final class WorkflowResourceDescriptionsProvider implements
        StateProvider<CallWorkflowParameters.WithError<Pair<Map<String, ResourceContentType>,
            Map<String, ResourceContentType>>, Exception>> {

        private Supplier<CallWorkflowParameters.WithError<WorkflowExecutionConnector, Exception>>
            m_workflowConnectionProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowConnectionProvider =
                initializer.computeFromProvidedState(CallWorkflowParameters.WorkflowExecutionConnectorProvider.class);
        }

        @Override
        public CallWorkflowParameters.WithError<Pair<Map<String, ResourceContentType>,
            Map<String, ResourceContentType>>, Exception> computeState(final NodeParametersInput parametersInput) {
            final var connectionOrError = m_workflowConnectionProvider.get();

            if (connectionOrError.hasError()) {
                return new CallWorkflowParameters.WithError<>(connectionOrError.exception());
            }

            try (var wfBackend = connectionOrError.value().createWorkflowBackend()) {
                final var inputResources = wfBackend.getInputResourceDescription();
                final var outputResources = wfBackend.getOutputResourceDescription();
                return new CallWorkflowParameters.WithError<>(new Pair<>(inputResources, outputResources));
            } catch (Exception e) {
                return new CallWorkflowParameters.WithError<>(e);
            }
        }
    }

    abstract static class AdditionalPortsToBeRemovedMessageProvider<T extends WorkflowParameterElement>
        implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<List<T>> m_paramsSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_paramsSupplier = initializer.computeFromValueSupplier(getElementsRef());
        }

        abstract Class<? extends ParameterReference<List<T>>> getElementsRef();

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            final var params = m_paramsSupplier.get();
            final var noParams = params.isEmpty();
            final var portLocations = getPortLocations(parametersInput);
            if (portLocations == null || portLocations.length <= params.size()) {
                if (noParams) {
                    return Optional.of(new TextMessage.Message(String.format("No %s parameters", getInputOrOutput()),
                        "", TextMessage.MessageType.SUCCESS));
                }
                return Optional.empty();
            }
            final var msg = new TextMessage.Message(
                String.format("%s %s port(s) will be removed", noParams ? "The" : "Additional", getInputOrOutput()),
                String.format(
                    "Applying the dialog will remove %s %s port(s) that are not needed for the selected workflow.",
                    (portLocations.length - params.size()), getInputOrOutput()),
                TextMessage.MessageType.INFO);
            return Optional.of(msg);

        }

        abstract String getInputOrOutput();

        abstract int[] getPortLocations(final NodeParametersInput parametersInput);
    }

    static class WorkflowParameterElement implements NodeParameters {

        abstract static class WorkflowParameterModifier<T extends WorkflowParameterElement>
            implements Modification.Modifier {

            @Override
            public void modify(final WidgetGroupModifier group) {
                group.find(AssignedToMessageRef.class).addAnnotation(TextMessage.class)
                    .withValue(getAssignedToMessageProvider()).modify();
                group.find(ParameterNameRef.class).addAnnotation(ValueReference.class).withValue(getParameterNameRef())
                    .modify();
                group.find(ParameterTypeRef.class).addAnnotation(ValueReference.class).withValue(getParameterTypeRef())
                    .modify();
            }

            abstract Class<? extends AssignedToMessageProvider<T>> getAssignedToMessageProvider();

            abstract Class<? extends ParameterReference<String>> getParameterNameRef();

            abstract Class<? extends ParameterReference<String>> getParameterTypeRef();

        }

        @Modification.WidgetReference(AssignedToMessageRef.class)
        Void m_assignedToMessage;

        interface AssignedToMessageRef extends Modification.Reference {
        }

        @Modification.WidgetReference(ParameterNameRef.class)
        String m_parameterName;

        interface ParameterNameRef extends Modification.Reference {
        }

        @Modification.WidgetReference(ParameterTypeRef.class)
        String m_portTypeId;

        interface ParameterTypeRef extends Modification.Reference {
        }

        WorkflowParameterElement() {
            // default constructor
        }

        WorkflowParameterElement(final String parameterName, final String portTypeId) {
            m_parameterName = parameterName;
            m_portTypeId = portTypeId;
        }

        PortType getPortType() {
            return getPortTypeById(m_portTypeId)
                .orElseThrow(() -> new IllegalStateException(String.format("Unknown port type id: %s", m_portTypeId)));
        }

        static Optional<PortType> getPortTypeById(final String portTypeId) {
            if (portTypeId == null) {
                return Optional.empty();
            }
            final var registry = PortTypeRegistry.getInstance();
            return registry.getObjectClass(portTypeId).map(registry::getPortType);
        }

        static String getPortTypeId(final PortType portType) {
            if (portType == null) {
                return null;
            }
            return portType.getPortObjectClass().getName();
        }

        WorkflowParameter toWorkflowParameter() {
            try {
                return new WorkflowParameter(m_parameterName, null, getPortType());
            } catch (InvalidSettingsException ise) {
                // TODO better error handling (in dialog) e.g. missing port type in submit client
                NodeLogger.getLogger(WorkflowParameterElement.class).error(ise.getMessage(), ise);
                return null;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(m_parameterName, m_portTypeId);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof WorkflowParameterElement other) {
                return Objects.equals(other.m_parameterName, m_parameterName)
                        && Objects.equals(other.m_portTypeId, m_portTypeId);
            }
            return false;
        }

        abstract static class AssignedToMessageProvider<T extends WorkflowParameterElement>
            implements StateProvider<Optional<TextMessage.Message>> {

            private Supplier<String> m_parameterNameSupplier;

            private Supplier<String> m_parameterTypeNameSupplier;

            private Supplier<List<T>> m_fullArraySupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                m_fullArraySupplier = initializer.computeFromValueSupplier(getElementsRef());
                m_parameterNameSupplier = initializer.getValueSupplier(getParameterNameRef());
                m_parameterTypeNameSupplier = initializer.getValueSupplier(getParameterTypeRef());
                /**
                 * Otherwise when the number of ports stays the same, we keep showing the old message although port
                 * types might have changed by applying the dialog
                 */
                ((StateProviderInitializerInternal)initializer).computeAfterApplyDialog();
            }

            abstract Class<? extends ParameterReference<List<T>>> getElementsRef();

            abstract Class<? extends ParameterReference<String>> getParameterNameRef();

            abstract Class<? extends ParameterReference<String>> getParameterTypeRef();

            @Override
            public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
                final var fullArray = m_fullArraySupplier.get();
                final var parameterName = m_parameterNameSupplier.get();
                final var parameterTypeId = m_parameterTypeNameSupplier.get();
                final var parameterType = getPortTypeById(parameterTypeId).orElseThrow(IllegalStateException::new);
                final var parameterTypeName = parameterType.getName();
                final var messageTitle = "Assigned to " + parameterName + " (" + parameterTypeName + ")";
                final var nodePortTypeOpt = getNodePortType(parametersInput, fullArray, parameterName);
                if (nodePortTypeOpt.isEmpty()) {
                    return Optional.of(new TextMessage.Message(messageTitle, //
                        String.format("Applying the dialog will add a %s port.", parameterTypeName),
                        TextMessage.MessageType.INFO));
                }
                final var nodePortType = nodePortTypeOpt.get();
                if (nodePortType.equals(parameterType)) {
                    return Optional.of(new TextMessage.Message(messageTitle, "", TextMessage.MessageType.SUCCESS));
                }
                return Optional.of(new TextMessage.Message(messageTitle, //
                    String.format("Applying the dialog will replace the port by a %s port.", parameterTypeName),
                    TextMessage.MessageType.INFO));
            }

            private Optional<PortType> getNodePortType(final NodeParametersInput parametersInput,
                final List<T> fullArray, final String parameterName) {
                final var paramIndex = findIndexWithParameterName(fullArray, parameterName);

                final var portsConfiguration = parametersInput.getPortsConfiguration();
                final var portLocations = getPortLocations(portsConfiguration);
                if (portLocations == null || portLocations.length <= paramIndex) {
                    return Optional.empty();
                }
                return Optional.of(getPortTypes(parametersInput)[portLocations[paramIndex]]);
            }

            abstract int[] getPortLocations(final PortsConfiguration portsConfiguration);

            abstract PortType[] getPortTypes(final NodeParametersInput parametersInput);

        }

        abstract static class PortTypeSubTitleProvider<T extends WorkflowParameterElement>
            implements StateProvider<String> {

            private Supplier<String> m_parameterNameSupplier;

            private Supplier<List<T>> m_fullArraySupplier;

            @Override
            public void init(final StateProviderInitializer initializer) {
                m_fullArraySupplier = initializer.computeFromValueSupplier(getParameterElementsRef());
                m_parameterNameSupplier = initializer.getValueSupplier(getParameterNameRef());
                /**
                 * Otherwise when the number of ports stays the same, we keep showing the old subtitles although port
                 * types might have changed by applying the dialog
                 */
                ((StateProviderInitializerInternal)initializer).computeAfterApplyDialog();
            }

            abstract Class<? extends ParameterReference<List<T>>> getParameterElementsRef();

            abstract Class<? extends ParameterReference<String>> getParameterNameRef();

            @Override
            public String computeState(final NodeParametersInput parametersInput)
                throws StateComputationFailureException {
                final var fullArray = m_fullArraySupplier.get();
                final var parameterName = m_parameterNameSupplier.get();
                final var paramIndex = findIndexWithParameterName(fullArray, parameterName);

                PortsConfiguration portsConfiguration = parametersInput.getPortsConfiguration();
                final var portLocations = getPortLocations(portsConfiguration);
                if (portLocations == null || portLocations.length <= paramIndex) {
                    return "not yet present";
                }
                return "Current type: " + getPortTypes(parametersInput)[portLocations[paramIndex]].getName();
            }

            abstract int[] getPortLocations(final PortsConfiguration portsConfiguration);

            abstract PortType[] getPortTypes(final NodeParametersInput parametersInput);

        }

    }

    abstract static class WorkflowParametersProvider<T extends WorkflowParameterElement>
        implements StateProvider<List<T>> {

        private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowParametersProvider.class);

        private Supplier<CallWorkflowParameters.WithError<Pair<Map<String, ResourceContentType>,
            Map<String, ResourceContentType>>, Exception>> m_resourceDescriptionsProvider;

        private Supplier<List<WorkflowParameterElement>> m_existingParametersProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_resourceDescriptionsProvider =
                initializer.computeFromProvidedState(WorkflowResourceDescriptionsProvider.class);
            m_existingParametersProvider = initializer.getValueSupplier(getParameterElementsInitialValueRef());
        }

        abstract Class<? extends ParameterReference<List<WorkflowParameterElement>>>
            getParameterElementsInitialValueRef();

        @Override
        public List<T> computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            final var resourcesOrError = m_resourceDescriptionsProvider.get();
            if (resourcesOrError.hasError()) {
                return List.of();
            }

            final var resources = getResourceDescriptionsFromPair(resourcesOrError.value());
            return mergeWithExisting(toParameterElements(resources), m_existingParametersProvider.get());
        }

        abstract Map<String, ResourceContentType> getResourceDescriptionsFromPair(
            Pair<Map<String, ResourceContentType>, Map<String, ResourceContentType>> pair);

        private List<T> toParameterElements(final Map<String, ResourceContentType> resources) {
            final var elements = new ArrayList<T>();
            for (var entry : resources.entrySet()) {
                final var parameterName = entry.getKey();
                String portTypeId;
                try {
                    portTypeId = entry.getValue().toPortType().getPortObjectClass().getName();
                } catch (IllegalArgumentException | NullPointerException | InvalidSettingsException e) {
                    LOGGER.debug("Failed to resolve port type for parameter: " + parameterName, e);
                    continue;
                }
                final var element = createParameterElement(parameterName, portTypeId);
                elements.add(element);
            }
            return elements;
        }

        abstract T createParameterElement(String parameterName, String portTypeId);

        /**
         * Merge new parameters with existing ones by preserving the existing order, deleting removed parameters,
         * updating the type of parameters where the type changed and adding all new parameters at the bottom
         *
         */
        private List<T> mergeWithExisting(final List<T> parameterElements,
            final List<WorkflowParameterElement> existingParameters) {
            final var mergedElements = new ArrayList<T>();
            final Map<String, T> newElementsMap =
                parameterElements.stream().collect(Collectors.toMap(e -> e.m_parameterName, e -> e));
            for (var existingElement : existingParameters) {
                final var matchingNewElement = newElementsMap.get(existingElement.m_parameterName);
                if (matchingNewElement != null) {
                    mergedElements.add(matchingNewElement);
                }

            }
            final Set<String> existingParameterNames =
                existingParameters.stream().map(e -> e.m_parameterName).collect(Collectors.toSet());
            for (var newElement : parameterElements) {
                if (!existingParameterNames.contains(newElement.m_parameterName)) {
                    mergedElements.add(newElement);
                }
            }
            return mergedElements;

        }
    }

    /**
     * We copy the initial workflow parameters for later use when merging with updated parameters and for determining
     * whether the dialog has to be made dirty artificially.
     */
    abstract static class InitialWorkflowParametersProvider<T extends WorkflowParameterElement>
        implements StateProvider<List<WorkflowParameterElement>> {

        private Supplier<List<T>> m_initialValueSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
            m_initialValueSupplier = initializer.getValueSupplier(getElementsRef());
            ((StateProviderInitializerInternal)initializer).computeAfterApplyDialog();
        }

        @Override
        public List<WorkflowParameterElement> computeState(final NodeParametersInput parametersInput) {
            return m_initialValueSupplier.get().stream().map(e -> (WorkflowParameterElement)e).toList();
        }

        abstract Class<? extends ParameterReference<List<T>>> getElementsRef();
    }

    private static <T extends WorkflowParameterElement> int findIndexWithParameterName(final List<T> elements,
        final String parameterName) {
        for (int i = 0; i < elements.size(); i++) {
            final var element = elements.get(i);
            if (element.m_parameterName != null && element.m_parameterName.equals(parameterName)) {
                return i;
            }
        }
        throw new IllegalStateException("Parameter name not found in array");
    }

}
