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

import java.util.Optional;

import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObjectSpec;
import org.knime.workflowservices.connection.util.ConnectionUtil;

@SuppressWarnings("restriction")
final class CommonParameters implements NodeParameters {

    @PersistEmbedded
    ExecutionParameters m_execution = new ExecutionParameters();

    @PersistEmbedded
    WorkflowOrDeploymentSelectionParameters m_workflowOrDeploymentSelection =
        new WorkflowOrDeploymentSelectionParameters();

    @PersistEmbedded
    TimeoutParameters m_timeout = new TimeoutParameters();

    static final class IsRemoteExecution implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(IsRemoteExecution::isRemoteExecution);
        }

        static boolean isRemoteExecution(final NodeParametersInput input) {
            if (IsHubAuthenticatorConnected.isHubAuthenticatorConnected(input)) {
                return true;
            }
            final var inFSSpec = getInputPortPosition(input).flatMap(input::getInPortSpec).orElse(null);
            return ConnectionUtil.isRemoteConnection(inFSSpec);
        }

    }

    static final class IsHubAuthenticatorConnected implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(IsHubAuthenticatorConnected::isHubAuthenticatorConnected);
        }

        static boolean isHubAuthenticatorConnected(final NodeParametersInput input) {
            final var position = getInputPortPosition(input);
            if (position.isEmpty()) {
                return false;
            }
            final var portObjectSpecClass = input.getInPortTypes()[position.get()].getPortObjectSpecClass();
            return AbstractHubAuthenticationPortObjectSpec.class.isAssignableFrom(portObjectSpecClass);
        }

        static Optional<AbstractHubAuthenticationPortObjectSpec>
            getConnectedHubAuthenticatorSpec(final NodeParametersInput input) {
            final var position = getInputPortPosition(input);
            if (position.isEmpty()) {
                return Optional.empty();
            }
            final var portObjectSpecOpt = input.getInPortSpec(position.get());
            if (portObjectSpecOpt.isPresent()
                && portObjectSpecOpt.get() instanceof AbstractHubAuthenticationPortObjectSpec spec) {
                return Optional.of(spec);
            }
            return Optional.empty();
        }

    }

    private static Optional<Integer> getInputPortPosition(final NodeParametersInput input) {
        final var position = input.getPortsConfiguration().getInputPortLocation()
            .get(CallWorkflowParameters.FILE_SYSTEM_CONNECTION_PORT_GROUP_ID);
        if (position == null || position.length == 0) {
            return Optional.empty();
        }
        return Optional.of(position[0]);
    }

}
