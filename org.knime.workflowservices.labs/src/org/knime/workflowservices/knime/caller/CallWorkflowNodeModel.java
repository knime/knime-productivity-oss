package org.knime.workflowservices.knime.caller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.container.ContainerTable;
import org.knime.core.data.container.DataContainer;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings;
import org.knime.core.node.exec.dataexchange.PortObjectIDSettings.ReferenceType;
import org.knime.core.node.exec.dataexchange.PortObjectRepository;
import org.knime.core.node.exec.dataexchange.in.PortObjectInNodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableTypeRegistry;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.capture.WorkflowPortObjectSpec;
import org.knime.core.node.workflow.capture.WorkflowSegment;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;
import org.knime.core.util.LockFailedException;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.productivity.base.callworkflow.IWorkflowBackend.WorkflowState;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.knime.util.CallWorkflowPayload;
import org.knime.workflowservices.knime.util.CallWorkflowUtil;
import org.knime.workflowservices.knime.util.WorkflowPortObjectPayload;

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
                if (payload instanceof WorkflowPortObjectPayload) {
                    //TODO The following code will be moved to the workflow port object on execute method
                    var po = ((WorkflowPortObjectPayload) payload).getWorkflowPortObject();
                    Set<NodeIDSuffix> portObjectReferenceReaderNodes = copyPortObjectReferenceReaderData(po, exec);
                    var wfmCopy = po.getSpec().getWorkflowSegment().loadWorkflow();
                    WorkflowSegment ws = new WorkflowSegment(wfmCopy, po.getSpec().getWorkflowSegment().getConnectedInputs(), po.getSpec().getWorkflowSegment().getConnectedOutputs(), portObjectReferenceReaderNodes);
                    outputPOs[i] = new WorkflowPortObject(new WorkflowPortObjectSpec(ws, po.getSpec().getWorkflowName(), po.getSpec().getInputIDs(), po.getSpec().getOutputIDs()));
                } else {
                    outputPOs[i] = payload.onExecute(exec, fv -> flowVarMap.put(fv.getName(), fv));
                }
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

    //TODO Should move this method to the WorkflowPortUtil, add a consumer as argument for the addPortObject
    private Set<NodeIDSuffix> copyPortObjectReferenceReaderData(final WorkflowPortObject workflowPortObject,
        final ExecutionContext exec) throws IOException, CanceledExecutionException, InvalidSettingsException, UnsupportedWorkflowVersionException, LockFailedException {
        Set<NodeIDSuffix> res = new HashSet<>();
        var wfm = workflowPortObject.getSpec().getWorkflowSegment().loadWorkflow();
        for (NodeContainer nc : wfm.getNodeContainers()) {
            if (nc instanceof NativeNodeContainer
                && ((NativeNodeContainer)nc).getNodeModel() instanceof PortObjectInNodeModel) {
                exec.setProgress("Copying data for node " + nc.getID());
                PortObjectInNodeModel portObjectReader =
                    (PortObjectInNodeModel)((NativeNodeContainer)nc).getNodeModel();
                final PortObjectIDSettings poSettings = portObjectReader.getInputNodeSettingsCopy();
                if (poSettings.getReferenceType() != ReferenceType.FILE) {
                    throw new IllegalStateException(
                        "Reference reader nodes expected to reference a file. But the reference type is "
                            + poSettings.getReferenceType());
                }
                URI uri = poSettings.getUri();
                var wfFile = wfm.getNodeContainerDirectory().getFile();
                File absoluteDataFile =
                    new File(wfFile, uri.toString().replace("knime://knime.workflow", ""));
                if (!absoluteDataFile.getCanonicalPath().startsWith(wfFile.getCanonicalPath())) {
                    throw new IllegalStateException(
                        "Trying to read in a data file outside of the workflow directory. Not allowed!");
                }
                PortObject po;
                try (InputStream in = absoluteDataFile.toURI().toURL().openStream()) {
                    po = readPortObject(exec, in, poSettings.isTable());
                }
                // TODO Should be passed as function or callable to move this method to WorkflowPortUtil
                UUID id = UUID.randomUUID();
                addPortObject(id, po);
                PortObjectRepository.add(id, po);
                updatePortObjectReferenceReaderReference(wfm, nc.getID(), poSettings, id);
                res.add(NodeIDSuffix.create(wfm.getID(), nc.getID()));
            }
        }
        return res;
    }

    //TODO Can move this method to WorkflowPortUtil
    private static void updatePortObjectReferenceReaderReference(final WorkflowManager wfm, final NodeID nodeId,
        final PortObjectIDSettings poSettings, final UUID id) throws InvalidSettingsException {
        poSettings.setId(id);

        final NodeSettings settings = new NodeSettings("root");
        wfm.saveNodeSettings(nodeId, settings);
        final NodeSettingsWO modelSettings = settings.addNodeSettings("model");
        poSettings.saveSettings(modelSettings);
        wfm.loadNodeSettings(nodeId, settings);
    }

    //TODO Can move this method to WorkflowPortUtil
    private static PortObject readPortObject(final ExecutionContext exec, final InputStream in, final boolean isTable)
        throws CanceledExecutionException, IOException {
        PortObject po;
        if (isTable) {
            try (ContainerTable table = DataContainer.readFromStream(in)) {
                po = exec.createBufferedDataTable(table, exec);
            }
        } else {
            po = PortUtil.readObjectFromStream(in, exec);
        }
        return po;
    }

}
