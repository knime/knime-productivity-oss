package org.knime.workflowservices.knime.caller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableTypeRegistry;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.productivity.base.callworkflow.IWorkflowBackend.WorkflowState;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.knime.util.CallWorkflowPayload;
import org.knime.workflowservices.knime.util.CallWorkflowUtil;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
class CallWorkflowNodeModel extends AbstractPortObjectRepositoryNodeModel {

    private IServerConnection m_serverConnection;

    /**
     * Node configuration (but a {@link CallWorkflowTableNodeConfiguration} is required to create the wf backend).
     */
    private final CallWorkflowNodeConfiguration m_configuration = new CallWorkflowNodeConfiguration();

    CallWorkflowNodeModel(final PortsConfiguration portConfig) {
        super(portConfig.getInputPorts(), portConfig.getOutputPorts());
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        // TODO copied code from AbstractCallWorkflowTableNodeModel
        PortObjectSpec connectionSpec = inSpecs[0];
        var conn = (FileSystemPortObjectSpec)connectionSpec;
        var currentWfm = NodeContext.getContext().getWorkflowManager();
        m_serverConnection = ServerConnectionUtil.getConnection(conn, currentWfm);
        return null;
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {

        Optional<WorkflowParameters> calleeProperties = m_configuration.getCalleeWorkflowProperties();
        if (calleeProperties.isEmpty()) {
            throw new IllegalStateException(
                "Call Workflow node is not properly configured: No callee workflow information available.");
        }

        return runInBackground(() -> {
            try (IWorkflowBackend backend = m_serverConnection.createWorkflowBackend(m_configuration)) {
                // TODO check freshness of callee signature (similar to ParameterUpdateWorker)
                CheckUtils.checkArgument(backend != null,
                    "Internal error: No backend available to execute callee workflow.");

                return executeWorkflow(backend, inObjects, calleeProperties.get(), exec);
            }
        });
    }

    /**
     * Executes the workflow locally or remote, depending on what {@link IWorkflowBackend} instance
     * {@link #m_serverConnection} provides.
     *
     * @param portObjects the data provided to this node's input ports, they are wrapped for transmission using
     *            {@link #createWorkflowInput(WorkflowParameters, PortObject[], Map, ExecutionContext)}
     * @param calleeWorkflowProperties description of callee workflow inputs and outputs
     * @param exec the {@link ExecutionContext} passed to {@link #execute(PortObject[], ExecutionContext)}
     * @return the result {@link PortObject}s computed by the callee workflow, in order of their output at the ports of
     *         this node
     * @throws Exception
     */
    private PortObject[] executeWorkflow(final IWorkflowBackend backend, final PortObject[] portObjects,
        final WorkflowParameters calleeWorkflowProperties, final ExecutionContext exec) throws Exception {

        // prepare an input object for each callee workflow input node
        VariableType<?>[] allTypes = VariableTypeRegistry.getInstance().getAllTypes();

        exec.setMessage("Preparing input data for callee workflow.");

        Collection<FlowVariable> flowVariables = getAvailableFlowVariables(allTypes).values();
        Map<String, ExternalNodeData> workflowInput = CallWorkflowUtil
            .createWorkflowInput(calleeWorkflowProperties.getInputParameters(), portObjects, flowVariables, exec);

        // execute and check success
        exec.setMessage("Executing callee workflow.");
        backend.loadWorkflow();
        WorkflowState state = backend.executeAsWorkflowService(workflowInput);
        CheckUtils.checkArgument(state == WorkflowState.EXECUTED, workflowExecutionFailureMessage(backend, state));

        // retrieve and restored callee workflow outputs
        exec.setMessage("Receiving results from callee workflow.");
        var outputNodes = calleeWorkflowProperties.getOutputParameters();
        var outputPOs = new PortObject[outputNodes.size()];
        // there might be multiple flow variable outputs in the callee (which is useless and a badly designed workflow)
        // map guarantees that flow vars are added only once
        var flowVarMap = new LinkedHashMap<String, FlowVariable>();
        for (var i = 0; i < outputNodes.size(); i++) {
            WorkflowParameter output = outputNodes.get(i);
            try (InputStream in = new BufferedInputStream(backend.openOutputResource(output.getParameterName()));
                    var payload = CallWorkflowPayload.createFrom(in, getOutPortType(i))) {
                outputPOs[i] = payload.onExecute(exec, fv -> flowVarMap.put(fv.getName(), fv));
            }
        }
        flowVarMap.values().stream().forEach(variable -> {
            VariableType expectedType = variable.getVariableType(); // NOSONAR must be declared as raw type
            pushFlowVariable(variable.getName(), expectedType, variable.getValue(expectedType));
        });
        return outputPOs;
    }

    @Override
    protected void reset() {
        // no internals
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_configuration.saveSettings(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.loadSettingsInModel(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new CallWorkflowNodeConfiguration().loadSettingsInModel(settings);
    }

    @Override
    protected void onDispose() {
        if (m_serverConnection != null) {
            try {
                m_serverConnection.close();
            } catch (IOException e) {
                getLogger().error("Error disposing server connection " + m_serverConnection.getClass().getSimpleName(),
                    e);
            }
        }
    }

    private static PortObject[] runInBackground(final Callable<PortObject[]> callable) throws Exception {
        try {
            return KNIMEConstants.GLOBAL_THREAD_POOL.runInvisible(callable);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                throw e;
            }
        }
    }

    /**
     * @param backend provides workflow error message
     * @param state the state the workflow is stuck in
     */
    private static String workflowExecutionFailureMessage(final IWorkflowBackend backend, final WorkflowState state) {
        String failureMessage = "Failure, workflow was not executed, current state is " + state + ".";
        String workflowMessage = backend.getWorkflowMessage();
        if (StringUtils.isNotBlank(workflowMessage)) {
            failureMessage = failureMessage + "\n" + workflowMessage;
        }
        return failureMessage;
    }

}
