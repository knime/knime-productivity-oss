/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 */

package org.knime.workflowservices;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.node.parameters.widget.message.TextMessage.Message;
import org.knime.workflowservices.CallWorkflowLayout.WorkflowOrDeploymentSection;
import org.knime.workflowservices.CommonParameters.IsHubAuthenticatorConnected;
import org.knime.workflowservices.WorkflowOrDeploymentSelectionParameters.DeploymentLoader.DeploymentsOrExeption;
import org.knime.workflowservices.connection.DeploymentExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * Workflow or deployment selection parameters for Call Workflow nodes.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
@Layout(WorkflowOrDeploymentSection.class)
class WorkflowOrDeploymentSelectionParameters implements NodeParameters {

    @PersistEmbedded
    @Effect(predicate = IsHubAuthenticatorConnected.class, type = EffectType.HIDE)
    RunWorkflowParameters m_runWorkflow = new RunWorkflowParameters();

    @Widget(title = "Deployment", //
        description = """
                The deployed workflow to execute. \
                This option is only visible when the node is connected to a KNIME Hub Authenticator node.
                """)
    @Effect(predicate = IsHubAuthenticatorConnected.class, type = EffectType.SHOW)
    @ChoicesProvider(DeploymentChoicesProvider.class)
    @ValueReference(DeploymentIdRef.class)
    String m_deploymentId;

    interface DeploymentIdRef extends ParameterReference<String> {
    }

    @TextMessage(HubAuthenticatorMissingMessageProvider.class)
    Void m_hubAuthenticatorMissingMessage;

    static final class DeploymentLoader extends CallWorkflowParameters.DependOnFetchConfig<DeploymentsOrExeption> {

        @Override
        public void additionalInit(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public DeploymentsOrExeption computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            if (!IsHubAuthenticatorConnected.isHubAuthenticatorConnected(parametersInput)) {
                return new DeploymentsOrExeption();
            }
            if (IsHubAuthenticatorConnected.getConnectedHubAuthenticatorSpec(parametersInput).isEmpty()) {
                return new DeploymentsOrExeption(
                    new IllegalArgumentException("No executed KNIME Hub Authenticator connected."));
            }
            try {
                final var fetchConfig = getFetchConfig(parametersInput);
                final var connection = ConnectionUtil.createConnection(fetchConfig);
                if (connection.isPresent()
                    && connection.get() instanceof DeploymentExecutionConnector deploymentConnection) {
                    return new DeploymentsOrExeption(deploymentConnection.getServiceDeployments());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new DeploymentsOrExeption(e);
            } catch (IOException | InvalidSettingsException e) {
                return new DeploymentsOrExeption(e);
            }
            return new DeploymentsOrExeption();

        }

        record DeploymentsOrExeption(List<Deployment> deployments, Exception exception) {

            DeploymentsOrExeption(final List<Deployment> deployments) {
                this(deployments, null);
            }

            DeploymentsOrExeption(final Exception exception) {
                this(List.of(), exception);
            }

            DeploymentsOrExeption() {
                this(List.of(), null);
            }
        }

    }

    static final class HubAuthenticatorMissingMessageProvider implements StateProvider<Optional<TextMessage.Message>> {

        private Supplier<DeploymentsOrExeption> m_deploymentsProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_deploymentsProvider = initializer.computeFromProvidedState(DeploymentLoader.class);

        }

        @Override
        public Optional<Message> computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            final var depOrEx = m_deploymentsProvider.get();
            if (depOrEx.exception() != null) {
                return Optional.of(new TextMessage.Message(//
                    "Failed to load deployments.", //
                    getCauseMessage(depOrEx), //
                    TextMessage.MessageType.ERROR//
                ));
            }
            return Optional.empty();
        }

        private static String getCauseMessage(final DeploymentsOrExeption depOrEx) {
            Throwable ex = depOrEx.exception();
            while (ex.getCause() != null) {
                ex = ex.getCause();
            }
            return ex.getMessage();
        }

    }

    /**
     * Choices provider for deployments. Fetches available deployments from Hub when connected via Hub Authenticator.
     */
    static final class DeploymentChoicesProvider implements StringChoicesProvider {

        private Supplier<DeploymentsOrExeption> m_deploymentsProvider;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_deploymentsProvider = initializer.computeFromProvidedState(DeploymentLoader.class);
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput parametersInput) {
            final var deployments = m_deploymentsProvider.get().deployments();
            return deployments.stream().map(deployment -> new StringChoice(deployment.id(), deployment.name()))
                .toList();
        }
    }

}
