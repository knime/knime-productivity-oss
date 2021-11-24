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
 *   Created on Nov 13, 2021 by wiswedel
 */
package org.knime.workflowservices.knime.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.knime.core.data.container.DataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.ICredentials;
import org.knime.core.node.workflow.IllegalFlowVariableNameException;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.util.FileUtil;
import org.knime.workflowservices.knime.CalleeWorkflowData;
import org.knime.workflowservices.knime.callee.WorkflowInputNodeModel;

/**
 *
 * @author wiswedel
 */
public final class CallWorkflowUtil {

    private CallWorkflowUtil() {
    }

    /**
     * TODO a similar check must be done somewhere else already, compare When sending flow variables from or to a callee
     * workflow, include only those that can be instantiated on the other side. For instance the flow variable with the
     * name "knime.workspace" is a reserved variable and can not be loaded using
     * {@link FlowVariable#load(NodeSettingsRO)} (which won't accept flow variables with reserved names).
     *
     * @param variable the flow variable to test for inclusion
     * @return whether the flow variable can be re-instantiated on the receiving side (the callee for a Workflow Input
     *         node, the caller for a Workflow Output node).
     */
    private static boolean isSendableFlowVariable(final FlowVariable variable) {
        try {
            FlowVariable.Scope.Flow.verifyName(variable.getName());
            return true;
        } catch (IllegalFlowVariableNameException e) {
            return false;
        }
    }

    /**
     * Prepare an {@link ExternalNodeData} instance for each of the Workflow Input nodes in the callee workflow. The
     * port objects will be written to files and set as resource on the created {@link ExternalNodeData} instances. The
     * same is done for the given flow variables, if there is at least one flow variable input parameter in the callee
     * workflow.
     *
     * @param inputs the input and output parameters of the workflow to be called
     * @param portObjects the data provided to this node's input ports
     * @param flowVariables flow variables to send to callee workflow if it contains an input parameter of type
     *            {@link FlowVariablePortObject}.
     * @param exec the {@link ExecutionContext} passed to {@link #execute(PortObject[], ExecutionContext)}
     *
     * @return a map from callee input parameter name to {@link ExternalNodeData} that contains the input data required
     *         for callee workflow execution
     *
     * @throws CanceledExecutionException
     * @throws IOException on
     */
    public static Map<String, ExternalNodeData> createWorkflowInput(
        final List<CalleeWorkflowData> inputs, final PortObject[] portObjects,
        final Collection<FlowVariable> flowVariables, final ExecutionContext exec)
        throws IOException, CanceledExecutionException {

        // TODO retrieve the target parameter for the port object at i-th input port from node configuration
        // currently, the i-th port object goes to the i-th input parameter

        Map<String, ExternalNodeData> workflowInput = new HashMap<>();

        // if there is at least one input parameter that expects flow variables, write them to a file (unlike other
        // port objects, flow variable port objects are pure markers without content)
        File serializedFlowVariables = null;
        if (inputs.stream().map(CalleeWorkflowData::getPortType).anyMatch(FlowVariablePortObject.TYPE::equals)) {
            // do not write flow variables with reserved names, such as knime.workspace
            // they can not be restored using FlowVariable.load - for good reasons

            serializedFlowVariables = writeFlowVariables(flowVariables);
        }

        // PERFORMANCE writing to disk can be expensive, parallelize this?
        // skip the optional FileSystemPortObject at index 0
        // for all other ports: serialize for transfer to callee workflow
        for (var input = 1; input < portObjects.length; input++) {
            final var portObject = portObjects[input];

            // identifier of the external node data object is the node id of the callee workflow Input node
            CalleeWorkflowData portDesc = inputs.get(input - 1);
            String key = portDesc.getParameterName();

            File tempFile = null;

            if (FlowVariablePortObject.TYPE.equals(portDesc.getPortType())) {
                // reuse the written file
                tempFile = serializedFlowVariables;
            } else {
                // serialize port object to temporary file
                tempFile = writePortObject(exec, portObject);
            }

            var externalNodeData = WorkflowInputNodeModel.createExternalNodeData(key, portDesc.getPortType(), tempFile);

            workflowInput.put(key, externalNodeData);
        }

        // TODO flow credentials: see AbstractCallWorkflowTableNodeModel#createFlowCredentialsNodeData

        return workflowInput;
    }

    /**
     * Serialize a {@link PortObject} to a file. The written file can be uploaded to a server for remote workflow
     * execution. This method will be called once for every input port, except for {@link FlowVariablePortObject}, see
     * {@link #writeFlowVariables(Collection)}.
     *
     * @param exec for writing the file
     * @param portObject the object to serialize
     * @return the file that contains the written {@link PortObject}
     * @throws IOException when creating a temporary file or writing to it
     * @throws CanceledExecutionException when being interrupted during writing to a file
     */
    public static File writePortObject(final ExecutionContext exec, final PortObject portObject)
        throws IOException, CanceledExecutionException {

        File tempFile;

        if (portObject instanceof BufferedDataTable) {
            // BufferedDataTables are historically not port objects and have their own methods for persistence
            tempFile = FileUtil.createTempFile("external-node-input-", ".table", false);
            DataContainer.writeToZip((BufferedDataTable)portObject, tempFile, exec);
        } else {
            // all other port objects can be written with PortUtil
            tempFile = FileUtil.createTempFile("external-node-input-", ".portobject", false);
            PortUtil.writeObjectToFile(portObject, tempFile, exec);
        }
        return tempFile;
    }

    /**
     * Writes a list of flow variables to a file.
     *
     * @param flowVariables the flow variables to write
     * @return the file containing the serialized flow variables
     * @throws IOException
     */
    public static File writeFlowVariables(final Collection<FlowVariable> flowVariables) throws IOException {

        List<FlowVariable> list = flowVariables.stream()//
            .filter(CallWorkflowUtil::isSendableFlowVariable)//
            .collect(Collectors.toList());

        // flow variable port objects don't contain information, they just serve as a means to connect nodes
        // take the flow variables from the workflow manager's stack and write them to XML via NodeSettings
        var variables = new NodeSettings("flow-variables");
        var variablesSettings = variables.addNodeSettings(FlowVariablesCallWorkflowPayload.CFG_PLAIN_VARIABLES);
        var passwordSettings = variables.addNodeSettings(FlowVariablesCallWorkflowPayload.CFG_PASSWORDS);
        for (var i = 0; i < list.size(); i++) {
            var flowVariable = list.get(i);
            String key = "Var_" + i;
            flowVariable.save(variablesSettings.addNodeSettings(key));
            if (flowVariable.getVariableType().equals(VariableType.CredentialsType.INSTANCE)) {
                ICredentials c = flowVariable.getValue(VariableType.CredentialsType.INSTANCE);
                passwordSettings.addPassword(flowVariable.getName(), FlowVariablesCallWorkflowPayload.WEAK_ENCRYPT_PASS,
                    c.getPassword());
            }
        }
        var tempFile = FileUtil.createTempFile("external-node-flow-variables-", ".xml", false);
        try (var out = new FileOutputStream(tempFile)) {
            variables.saveToXML(out);
        }
        return tempFile;
    }

}
