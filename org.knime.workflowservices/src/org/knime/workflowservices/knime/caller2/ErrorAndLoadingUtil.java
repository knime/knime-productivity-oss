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

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.util.Pair;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowParameters.WorkflowFetchIsRunning;
import org.knime.workflowservices.ErrorMessageProvider;
import org.knime.workflowservices.IWorkflowBackend;

final class ErrorAndLoadingUtil {

    private ErrorAndLoadingUtil() {
        // prevent instantiation
    }

    static final class ParametersNotReady implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return or(i.getPredicate(WorkflowFetchIsRunning.class),
                i.getPredicate(HasWorkflowParametersErrorMessage.class));
        }

    }

    /**
     * Always present but sometimes hidden message indicating that input parameters are being loaded.
     */
    static final class LoadingParametersMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeBeforeOpenDialog();
        }

        @Override
        public Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
            return Optional.of(new TextMessage.Message("Loading workflow parameters...",
                "The parameters are being loaded from the selected workflow.", TextMessage.MessageType.INFO));
        }
    }

    /**
     * Error message shown when there is an error loading workflow parameters.
     */
    static final class WorkflowParametersErrorMessageProvider extends ErrorMessageProvider<Pair< //
            Map<String, IWorkflowBackend.ResourceContentType>, Map<String, IWorkflowBackend.ResourceContentType>>> {

        WorkflowParametersErrorMessageProvider() {
            super("Failed to load workflow parameters",
                WorkflowParametersUtil.WorkflowResourceDescriptionsProvider.class);
        }

    }

    static final class HasWorkflowParametersErrorMessage implements StateProvider<Boolean>, BooleanReference {

        private Supplier<Optional<TextMessage.Message>> m_errorMessageProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_errorMessageProvider = initializer.computeFromProvidedState(WorkflowParametersErrorMessageProvider.class);
        }

        @Override
        public Boolean computeState(final NodeParametersInput parametersInput) {
            return m_errorMessageProvider.get().isPresent();
        }
    }
}
