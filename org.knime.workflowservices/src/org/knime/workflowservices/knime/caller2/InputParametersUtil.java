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
import java.util.Map;

import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.core.util.Pair;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.workflowservices.IWorkflowBackend.ResourceContentType;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.InputParametersRef;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.AdditionalPortsToBeRemovedMessageProvider;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.InitialWorkflowParametersProvider;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParametersProvider;

@SuppressWarnings("restriction")
final class InputParametersUtil {

    private InputParametersUtil() {
        // prevent instantiation
    }

    static final class NoInputParameters implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getList(InputParametersRef.class).isEmpty();
        }

    }

    @Modification(InputParameterElement.InputParametersModifier.class)
    static final class InputParameterElement extends WorkflowParameterElement {

        InputParameterElement() {
            super();
        }

        InputParameterElement(final String parameterName, final String portTypeId) {
            super(parameterName, portTypeId);
        }

        interface ParameterNameRef extends ParameterReference<String> {
        }

        interface ParameterTypeRef extends ParameterReference<String> {
        }

        static final class InputParametersModifier extends WorkflowParameterModifier<InputParameterElement> {

            @Override
            Class<? extends AssignedToMessageProvider<InputParameterElement>> getAssignedToMessageProvider() {
                return AssignedToInputPortMessageProvider.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterNameRef() {
                return ParameterNameRef.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterTypeRef() {
                return ParameterTypeRef.class;
            }
        }

        static final class AssignedToInputPortMessageProvider extends AssignedToMessageProvider<InputParameterElement> {

            @Override
            Class<? extends ParameterReference<List<InputParameterElement>>> getElementsRef() {
                return InputParametersRef.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterNameRef() {
                return ParameterNameRef.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterTypeRef() {
                return ParameterTypeRef.class;
            }

            @Override
            int[] getPortLocations(final PortsConfiguration portsConfiguration) {
                return portsConfiguration.getInputPortLocation().get(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
            }

            @Override
            PortType[] getPortTypes(final NodeParametersInput parametersInput) {
                return parametersInput.getInPortTypes();
            }

        }

        static final class InputPortTypeSubTitleProvider extends PortTypeSubTitleProvider<InputParameterElement> {

            @Override
            Class<? extends ParameterReference<List<InputParameterElement>>> getParameterElementsRef() {
                return InputParametersRef.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterNameRef() {
                return ParameterNameRef.class;
            }

            @Override
            int[] getPortLocations(final PortsConfiguration portsConfiguration) {
                return portsConfiguration.getInputPortLocation().get(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
            }

            @Override
            PortType[] getPortTypes(final NodeParametersInput parametersInput) {
                return parametersInput.getInPortTypes();
            }
        }

    }

    static final class InputParametersProvider extends WorkflowParametersProvider<InputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<WorkflowParameterElement>>> getParameterElementsInitialValueRef() {
            return InputParametersInitialValueRef.class;
        }

        @Override
        Map<String, ResourceContentType> getResourceDescriptionsFromPair(
            final Pair<Map<String, ResourceContentType>, Map<String, ResourceContentType>> pair) {
            return pair.getFirst();
        }

        @Override
        InputParameterElement createParameterElement(final String parameterName, final String portTypeId) {
            return new InputParameterElement(parameterName, portTypeId);
        }

    }

    static final class AdditionalInputPortsToBeRemovedMessageProvider
        extends AdditionalPortsToBeRemovedMessageProvider<InputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<InputParameterElement>>> getElementsRef() {
            return InputParametersRef.class;
        }

        @Override
        int[] getPortLocations(final NodeParametersInput parametersInput) {
            return parametersInput.getPortsConfiguration().getInputPortLocation()
                .get(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
        }

        @Override
        String getInputOrOutput() {
            return "input";
        }

    }

    static final class InitialInputParametersProvider extends InitialWorkflowParametersProvider<InputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<InputParameterElement>>> getElementsRef() {
            return InputParametersRef.class;
        }

    }
}
