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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.core.webui.node.dialog.defaultdialog.internal.widget.PersistWithin.PersistEmbedded;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.workflowservices.CommonParameters.IsHubAuthenticatorConnected;
import org.knime.workflowservices.DialogTempState.CopiedProcessingUuidRef;
import org.knime.workflowservices.DialogTempState.ProvideTrueOnUnfinishedProcessingUuid;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * Common parameters shared across all Call Workflow nodes. These parameters handle workflow selection, execution mode,
 * and advanced settings.
 *
 * Use the {@link WorkflowExecutionConnectorProvider} to obtain a {@link WorkflowExecutionConnector} based on the
 * current node parameters and make the rest of the dialog depend on that.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @since 5.10
 */
@LoadDefaultsForAbsentFields
@SuppressWarnings("restriction")
public class CallWorkflowParameters implements NodeParameters {

    /**
     * We hard-code the "File System Connection" here since otherwise we would need to implement all state providers for
     * all three nodes "Call Workflow (Table Based)", "Call Workflow (Row Based)" and "Call Workflow Service"
     * separately.
     */
    static final String FILE_SYSTEM_CONNECTION_PORT_GROUP_ID = "File System Connection";

    @PersistEmbedded
    @ValueReference(CommonParametersRef.class)
    CommonParameters m_common = new CommonParameters();

    interface CommonParametersRef extends ParameterReference<CommonParameters> {
    }

    @Persistor(DialogTempState.DoNotPersist.class)
    DialogTempState m_dialogState = new DialogTempState();

    /**
     * If present in the node settings and set to true, we invalidate the settings.
     */
    public static final String WORKFLOW_IS_LOADING_CFG_KEY = "workflowConnectionIsLoading";

    /**
     * Indicates whether a workflow fetch is currently running, i.e. whether the two above UUIDs are equal.
     */
    @ValueProvider(ProvideTrueOnUnfinishedProcessingUuid.class)
    @ValueReference(WorkflowFetchIsRunning.class)
    @Persist(configKey = WORKFLOW_IS_LOADING_CFG_KEY)
    boolean m_workflowConnectionIsLoading;

    /**
     * Use this predicate/state to determine whether a workflow fetch is currently running.
     */
    public static final class WorkflowFetchIsRunning implements BooleanReference {
    }

    /**
     * Extend this base class to add triggers and continue to compute a state that depends on the fetch configuration.
     */
    abstract static class DependOnFetchConfig<T> implements StateProvider<T> {

        private Supplier<CommonParameters> m_parametersSupplier;

        @Override
        public final void init(final StateProviderInitializer initializer) {
            m_parametersSupplier = initializer.getValueSupplier(CommonParametersRef.class);
            additionalInit(initializer);
        }

        abstract void additionalInit(final StateProviderInitializer initializer);

        protected CallWorkflowConnectionConfiguration getFetchConfig(final NodeParametersInput input)
            throws InvalidSettingsException {
            final var tempParameters = new CallWorkflowParameters();
            final var commonParameters = m_parametersSupplier.get();
            tempParameters.m_common = commonParameters;

            final var tempSettings = new NodeSettings("temp");

            NodeParametersUtil.saveSettings(CallWorkflowParameters.class, tempParameters, tempSettings);

            final var nodeCreationConfig = ((NativeNodeContainer)NodeContext.getContext().getNodeContainer()).getNode()
                .getCopyOfCreationConfig().orElseThrow(IllegalStateException::new);
            final var tempConfig =
                new CallWorkflowConnectionConfiguration(nodeCreationConfig, FILE_SYSTEM_CONNECTION_PORT_GROUP_ID);

            tempConfig.loadSettingsInModel(tempSettings);
            IsHubAuthenticatorConnected.getConnectedHubAuthenticatorSpec(input)
                .ifPresent(tempConfig::setHubAuthentication);
            return tempConfig.createFetchConfiguration();
        }

        protected String getWorkflowPath() {
            final var commonParams = m_parametersSupplier.get();
            return commonParams.m_workflowOrDeploymentSelection.m_runWorkflow.m_workflowPath.m_path.getPath();
        }

    }

    /**
     * A generic type that holds either a value or an exception.
     *
     * @param value a value that is only present if no exception occurred
     * @param exception that is only present if an exception occurred
     *
     * @param <V> The value type
     * @param <E> The exception type
     */
    public record WithError<V, E extends Exception>(V value, E exception) {

        /**
         * Positive case constructor.
         *
         * @param value the value
         */
        public WithError(final V value) {
            this(value, null);
        }

        /**
         * Negative case constructor.
         *
         * @param exception the exception
         */
        public WithError(final E exception) {
            this(null, exception);
        }

        /**
         * Check before accessing the exception.
         *
         * @return true if an exception occurred
         */
        public boolean hasError() {
            return exception != null;
        }

    }

    /**
     * Provides the workflow execution connector based on the current dialog parameters.
     */
    public static final class WorkflowExecutionConnectorProvider
        extends DependOnFetchConfig<WithError<WorkflowExecutionConnector, Exception>> {

        private Supplier<String> m_currentUuidSupplier;

        @Override
        public void additionalInit(final StateProviderInitializer initializer) {
            m_currentUuidSupplier = initializer.computeFromValueSupplier(CopiedProcessingUuidRef.class);
        }

        private static final Map<NodeID, Map<String, Thread>> RUNNING_PER_NODE = new LinkedHashMap<>();

        static NodeID getCurrentNodeID() {
            return NodeContext.getContext().getNodeContainer().getID();
        }

        static Map<String, Thread> getRunningForNode() {
            return RUNNING_PER_NODE.computeIfAbsent(getCurrentNodeID(), k -> new LinkedHashMap<>());
        }

        /**
         * Method for diagnostic purposes in tests.
         *
         * @param nodeID the node ID
         * @return number of currently running threads
         */
        public static int getNumRunningThreads(final NodeID nodeID) {
            return RUNNING_PER_NODE.get(nodeID).size();
        }

        static void cancelIfRunningAndRemove(final String uuid) {
            synchronized (RUNNING_PER_NODE) {
                final var runningForNode = getRunningForNode();
                final var thread = runningForNode.get(uuid);
                if (thread != null) {
                    thread.interrupt();
                    runningForNode.remove(uuid);
                }
            }
        }

        static void remove(final String uuid) {
            synchronized (RUNNING_PER_NODE) {
                final var runningForNode = getRunningForNode();
                runningForNode.remove(uuid);
            }
        }

        /**
         * Called on deactivation
         *
         * @param nodeID the node ID
         */
        public static void terminateAndClearAllRunningThreads(final NodeID nodeID) {
            synchronized (RUNNING_PER_NODE) {
                final var runningForNode = RUNNING_PER_NODE.get(nodeID);
                if (runningForNode != null) {
                    for (final var thread : runningForNode.values()) {
                        thread.interrupt();
                    }
                    RUNNING_PER_NODE.remove(nodeID);
                }
            }
        }

        @Override
        public WithError<WorkflowExecutionConnector, Exception> computeState(final NodeParametersInput parametersInput)
            throws StateComputationFailureException {
            synchronized (RUNNING_PER_NODE) {
                final var currentUuid = m_currentUuidSupplier.get();
                final var runningForNode = getRunningForNode();
                runningForNode.put(currentUuid, Thread.currentThread());
            }
            try {
                final var fetchConfig = getFetchConfig(parametersInput);
                if (IsHubAuthenticatorConnected.isHubAuthenticatorConnected(parametersInput)) {
                    if (fetchConfig.getHubAuthentication() == null) {
                        throw new IllegalStateException("No executed KNIME Hub Authenticator connected.");
                    }
                } else {
                    final var workflowPath = getWorkflowPath();
                    if (workflowPath == null || workflowPath.isEmpty()) {
                        throw new IllegalStateException(
                            "Select a workflow by entering a path or browsing a file system.");
                    }
                }
                final var connection = ConnectionUtil.createConnection(fetchConfig);
                if (connection.isEmpty()) {
                    return new WithError<>(new IllegalStateException("Failed to create connection"));
                }
                return new WithError<>(connection.get());
            } catch (Exception e) {
                return new WithError<>(e);
            }

        }

    }

}
