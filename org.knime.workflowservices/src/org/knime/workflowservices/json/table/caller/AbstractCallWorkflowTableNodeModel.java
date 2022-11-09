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
 *   Created on May 24, 2018 by Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
package org.knime.workflowservices.json.table.caller;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.inactive.InactiveBranchPortObject;
import org.knime.core.node.workflow.CredentialsProvider;
import org.knime.core.node.workflow.ICredentials;
import org.knime.json.node.container.input.credentials.ContainerCredentialMapper;
import org.knime.json.node.container.input.variable2.ContainerVariableMapper2;
import org.knime.json.node.container.mappers.ContainerTableMapper;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.IWorkflowBackend.WorkflowState;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.json.table.caller2.CallWorkflowTable2NodeFactory;

/**
 * Model for the Call Workflow (Table Based) nodes.
 *
 * Used for the {@link CallWorkflowTable2NodeFactory}.
 *
 * Also used by the deprecated nodes {@link CallWorkflowTableNodeFactory} and
 * {@link Post43CallWorkflowTableNodeFactory}.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
public abstract class AbstractCallWorkflowTableNodeModel extends NodeModel {

    protected final CallWorkflowTableNodeConfiguration m_configuration;

    protected IServerConnection m_serverConnection;

    /**
     * Creates a new model.
     *
     * @deprecated only for old call workflow nodes that have a fixed optional file system port object
     */
    @Deprecated
    protected AbstractCallWorkflowTableNodeModel(final PortType connectionType) {
        super(new PortType[]{connectionType, BufferedDataTable.TYPE_OPTIONAL}, new PortType[]{BufferedDataTable.TYPE});
        m_configuration = new CallWorkflowTableNodeConfiguration();
    }

    /**
     * @param inputPorts TODO
     * @param outputPorts
     */
    protected AbstractCallWorkflowTableNodeModel(final PortType[] inputPorts, final PortType[] outputPorts,
        final CallWorkflowTableNodeConfiguration configuration) {
        super(inputPorts, outputPorts);
        m_configuration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        try {
            return KNIMEConstants.GLOBAL_THREAD_POOL.runInvisible(new Callable<PortObject[]>() {
                @Override
                public PortObject[] call() throws Exception {
                    return executeInternal(inObjects, exec);
                }
            });
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                throw e;
            }
        }
    }

    private PortObject[] executeInternal(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        final BufferedDataTable table = getInputTable(inObjects);
        try (IWorkflowBackend backend = m_serverConnection.createWorkflowBackend(m_configuration)) {
            if (backend != null) {
                backend.loadWorkflow();
                WorkflowState state = backend.execute(createWorkflowInput(table));
                if (state != WorkflowState.EXECUTED) {
                    String failureMessage = "Failure, workflow was not executed, current state is " + state + ".";
                    String workflowMessage = backend.getWorkflowMessage();
                    if (StringUtils.isNotBlank(workflowMessage)) {
                        failureMessage = failureMessage + "\n" + workflowMessage;
                    }
                    throw new Exception(failureMessage);
                } else {
                    return getOutputFromExecutedWorkflow(exec, backend);
                }
            } else {
                throw new Exception(
                    "This node cannot be executed without a server connection in a temporary copy of a workflow");
            }
        }
    }

    /**
     * @param inObjects the connected input port objects
     * @return the port object that provides access to the data to process
     */
    protected abstract BufferedDataTable getInputTable(PortObject[] inObjects);

    private Map<String, ExternalNodeData> createWorkflowInput(final BufferedDataTable table)
        throws InvalidSettingsException {
        Map<String, ExternalNodeData> workflowInput = new HashMap<>();

        String tableDestination = m_configuration.getSelectedInputParameter();
        createTableData(table, tableDestination).ifPresent(tableData -> workflowInput.put(tableDestination, tableData));

        String flowVariableDestination = m_configuration.getFlowVariableDestination();
        createFlowVariableData(flowVariableDestination)
            .ifPresent(flowVariableData -> workflowInput.put(flowVariableDestination, flowVariableData));

        String flowCredentialsDestination = m_configuration.getFlowCredentialsDestination();
        createFlowCredentialsNodeData(flowCredentialsDestination)
            .ifPresent(flowCredentialsData -> workflowInput.put(flowCredentialsDestination, flowCredentialsData));

        return workflowInput;
    }

    private static Optional<ExternalNodeData> createTableData(final BufferedDataTable table, final String id)
        throws InvalidSettingsException {
        if (StringUtils.isNotBlank(id) && table != null) {
            JsonValue inputTableAsJson = ContainerTableMapper.toContainerTableJsonValue(table);
            ExternalNodeData externalNodeData = ExternalNodeData.builder(id).jsonValue(inputTableAsJson).build();

            return Optional.of(externalNodeData);
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param id the name of the input parameter to which the flow variable data is pushed to
     * @return the flow variable payload to be associated to the input parameter's name
     */
    private Optional<ExternalNodeData> createFlowVariableData(final String id) throws InvalidSettingsException {
        if (StringUtils.isNotBlank(id)) {
            // Technically, we would have to infer from the template supplied by the CI(V) node if it uses a
            // simplified schema or a full schema. We omit this for sake of simplicity, and decree that CWf must not
            // support CI(V) nodes using the simplified schema. Follow-up ticket: AP-17619.
            JsonValue variablesAsJson =
                ContainerVariableMapper2.toContainerVariableJsonValue(getAvailableFlowVariables(), false);
            ExternalNodeData externalNodeData = ExternalNodeData.builder(id).jsonValue(variablesAsJson).build();

            return Optional.of(externalNodeData);
        } else {
            return Optional.empty();
        }
    }

    private Optional<ExternalNodeData> createFlowCredentialsNodeData(final String id) throws InvalidSettingsException {
        if (StringUtils.isNotBlank(id)) {
            CredentialsProvider credentialsProvider = getCredentialsProvider();

            List<ICredentials> credentials =
                credentialsProvider.listNames().stream().filter(CallWorkflowUtil::verifyCredentialIdentifier)
                    .map(name -> credentialsProvider.get(name)).collect(Collectors.toList());

            JsonValue jsonValue = ContainerCredentialMapper.toContainerCredentialsJsonValue(credentials);

            ExternalNodeData externalNodeData = ExternalNodeData.builder(id).jsonValue(jsonValue).build();
            return Optional.of(externalNodeData);
        } else {
            return Optional.empty();
        }
    }

    private PortObject[] getOutputFromExecutedWorkflow(final ExecutionContext exec, final IWorkflowBackend backend)
        throws InvalidSettingsException {
        JsonValue output = getOutputJsonValue(backend);
        if (output == null) {
            return new PortObject[]{InactiveBranchPortObject.INSTANCE};
        } else {
            return ContainerTableMapper.toBufferedDataTable(output, exec);
        }
    }

    private JsonValue getOutputJsonValue(final IWorkflowBackend backend) {
        String parameterId = m_configuration.getSelectedOutputParameter();

        JsonValue result = null;
        Map<String, JsonValue> fullyQualifiedOutputValues = backend.getOutputValues();
        if (m_configuration.isUseFullyQualifiedId()) {
            result = fullyQualifiedOutputValues.get(parameterId);
        } else {
            result = findJsonValueBasedOnSimpleParameterName(parameterId, fullyQualifiedOutputValues);
        }
        return result;
    }

    private static JsonValue findJsonValueBasedOnSimpleParameterName(final String parameterId,
        final Map<String, JsonValue> fullyQualifiedOutputValues) {
        Map<String, String> fullyQualifiedToSimpleIDMap =
            IWorkflowBackend.getFullyQualifiedToSimpleIDMap(fullyQualifiedOutputValues.keySet());
        for (Entry<String, JsonValue> fullyQualifiedOutputValue : fullyQualifiedOutputValues.entrySet()) {
            String simpleParameterId = fullyQualifiedToSimpleIDMap.get(fullyQualifiedOutputValue.getKey());
            if (parameterId.equals(simpleParameterId)) {
                return fullyQualifiedOutputValue.getValue();
            }
        }
        return null;
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_configuration.save(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.validateConfigurationForModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.loadInModel(settings, m_serverConnection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // no internals
    }

    /**
     * {@inheritDoc}
     */
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

}
