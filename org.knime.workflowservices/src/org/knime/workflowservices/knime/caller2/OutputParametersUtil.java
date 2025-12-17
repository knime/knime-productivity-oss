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
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersInitialValueRef;
import org.knime.workflowservices.knime.caller2.CalleeParameters.OutputParametersRef;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.AdditionalPortsToBeRemovedMessageProvider;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.InitialWorkflowParametersProvider;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParameterElement;
import org.knime.workflowservices.knime.caller2.WorkflowParametersUtil.WorkflowParametersProvider;

@SuppressWarnings("restriction")
final class OutputParametersUtil {

    private OutputParametersUtil() {
        // prevent instantiation
    }

    static final class NoOutputParameters implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getList(OutputParametersRef.class).isEmpty();
        }

    }

    @Modification(OutputParameterElement.OutputParametersModifier.class)
    static final class OutputParameterElement extends WorkflowParameterElement {

        OutputParameterElement() {
            super();
        }

        OutputParameterElement(final String parameterName, final String portTypeId) {
            super(parameterName, portTypeId);
        }

        interface ParameterNameRef extends ParameterReference<String> {
        }

        interface ParameterTypeRef extends ParameterReference<String> {
        }

        static final class OutputParametersModifier extends WorkflowParameterModifier<OutputParameterElement> {

            @Override
            Class<? extends AssignedToMessageProvider<OutputParameterElement>> getAssignedToMessageProvider() {
                return AssignedToOutputPortMessageProvider.class;
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

        static final class AssignedToOutputPortMessageProvider
            extends AssignedToMessageProvider<OutputParameterElement> {

            @Override
            Class<? extends ParameterReference<List<OutputParameterElement>>> getElementsRef() {
                return OutputParametersRef.class;
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
                return portsConfiguration.getOutputPortLocation().get(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
            }

            @Override
            PortType[] getPortTypes(final NodeParametersInput parametersInput) {
                return parametersInput.getOutPortTypes();
            }
        }

        static final class OutputPortTypeSubTitleProvider extends PortTypeSubTitleProvider<OutputParameterElement> {

            @Override
            Class<? extends ParameterReference<List<OutputParameterElement>>> getParameterElementsRef() {
                return OutputParametersRef.class;
            }

            @Override
            Class<? extends ParameterReference<String>> getParameterNameRef() {
                return ParameterNameRef.class;
            }

            @Override
            int[] getPortLocations(final PortsConfiguration portsConfiguration) {
                return portsConfiguration.getOutputPortLocation().get(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
            }

            @Override
            PortType[] getPortTypes(final NodeParametersInput parametersInput) {
                return parametersInput.getOutPortTypes();
            }
        }

    }

    static final class OutputParametersProvider extends WorkflowParametersProvider<OutputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<WorkflowParameterElement>>> getParameterElementsInitialValueRef() {
            return OutputParametersInitialValueRef.class;
        }

        @Override
        Map<String, ResourceContentType> getResourceDescriptionsFromPair(
            final Pair<Map<String, ResourceContentType>, Map<String, ResourceContentType>> pair) {
            return pair.getSecond();
        }

        @Override
        OutputParameterElement createParameterElement(final String parameterName, final String portTypeId) {
            return new OutputParameterElement(parameterName, portTypeId);
        }

    }

    static final class AdditionalOutputPortsToBeRemovedMessageProvider
        extends AdditionalPortsToBeRemovedMessageProvider<OutputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<OutputParameterElement>>> getElementsRef() {
            return OutputParametersRef.class;
        }

        @Override
        int[] getPortLocations(final NodeParametersInput parametersInput) {
            return parametersInput.getPortsConfiguration().getOutputPortLocation()
                .get(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
        }

        @Override
        String getInputOrOutput() {
            return "output";
        }

    }

    static final class InitialOutputParametersProvider
        extends InitialWorkflowParametersProvider<OutputParameterElement> {

        @Override
        Class<? extends ParameterReference<List<OutputParameterElement>>> getElementsRef() {
            return OutputParametersRef.class;
        }

    }

}
