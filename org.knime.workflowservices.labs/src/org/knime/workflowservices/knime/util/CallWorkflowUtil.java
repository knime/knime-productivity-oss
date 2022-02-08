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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.capture.ReferenceReaderDataUtil;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.util.FileUtil;
import org.knime.productivity.base.callworkflow.IWorkflowBackend.ResourceContentType;
import org.knime.workflowservices.knime.caller.WorkflowParameter;

/**
 *
 * @author wiswedel
 */
public final class CallWorkflowUtil {

    private CallWorkflowUtil() {
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
     * @param exec to write port objects
     *
     * @return a map from callee input parameter name to {@link ExternalNodeData} that contains the input data required
     *         for callee workflow execution
     *
     * @throws CanceledExecutionException
     * @throws IOException on
     */
    public static Map<String, ExternalNodeData> createWorkflowInput(final List<WorkflowParameter> inputs,
        final PortObject[] portObjects, final Collection<FlowVariable> flowVariables, final ExecutionContext exec)
        throws IOException, CanceledExecutionException {

        Map<String, ExternalNodeData> workflowInput = new HashMap<>();

        // if there is at least one input parameter that expects flow variables, write them to a file (unlike other
        // port objects, flow variable port objects are pure markers without content)
        File serializedFlowVariables = null;
        if (inputs.stream().map(WorkflowParameter::getPortType).anyMatch(FlowVariablePortObject.TYPE::equals)) {
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
            WorkflowParameter portDesc = inputs.get(input - 1);
            String key = portDesc.getParameterName();

            File tempFile = null;

            if (FlowVariablePortObject.TYPE.equals(portDesc.getPortType())) {
                // reuse the written file
                tempFile = serializedFlowVariables;
            } else if (WorkflowPortObject.TYPE.equals(portDesc.getPortType())) {
                tempFile = writeWorkflowPortObjectAndReferencedData((WorkflowPortObject)portObject, exec);
            } else {
                // serialize port object to temporary file
                tempFile = writePortObject(exec, portObject);
            }

            var externalNodeData = createExternalNodeData(key, portDesc.getPortType(), tempFile);

            workflowInput.put(key, externalNodeData);
        }

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
        if (portObject instanceof BufferedDataTable) {
            return TableCallWorkflowPayload.writeTable(exec, (BufferedDataTable)portObject);
        } else {
            return PortObjectCallWorkflowPayload.writePortObject(exec, portObject);
        }
    }

    /**
     * Writes a list of flow variables to a file.
     *
     * @param flowVariables the flow variables to write
     * @return the file containing the serialized flow variables
     * @throws IOException
     */
    public static File writeFlowVariables(final Collection<FlowVariable> flowVariables) throws IOException {
        return FlowVariablesCallWorkflowPayload.writeFlowVariables(flowVariables);
    }

    /**
     * @param parameterName the workflow parameter name
     * @param portType specifies the type of content in the file
     * @param portContent as written by, e.g., {@link CallWorkflowUtil#writePortObject(ExecutionContext, PortObject)}
     * @return data for a workflow input parameter or from a workflow output parameter, with the port object contents in
     *         a file
     */
    public static ExternalNodeData createExternalNodeData(final String parameterName, final PortType portType,
        final File portContent) {
        return ExternalNodeData.builder(parameterName)//
            .resource(portContent == null ? UriBuilder.fromUri("file:/dev/null").build() : portContent.toURI())//
            .contentType(ResourceContentType.of(portType).asString()) //
            .build();
    }

    /**
     * Writes a copy of the WorkflowPortObject and it's referenced reader data to file
     *
     * @param po <T> a {@link WorkflowPortObject}
     * @param exec an {@link ExecutionMonitor}
     * @return a {@link File}
     * @throws IOException
     * @throws CanceledExecutionException
     */
    public static File writeWorkflowPortObjectAndReferencedData(final WorkflowPortObject po,
        final ExecutionMonitor exec) throws IOException, CanceledExecutionException {

        var segment = po.getSpec().getWorkflowSegment();
        var poCopy = po.transformAndCopy(wfm -> {
            var wfDir = wfm.getNodeContainerDirectory().getFile();
            var dataDir = new File(wfDir, "data");
            dataDir.mkdir();
            wfm.setName(po.getSpec().getWorkflowName());
            try {
                ReferenceReaderDataUtil.writeReferenceReaderData(wfm, segment.getPortObjectReferenceReaderNodes(), dataDir, exec);
            } catch (IOException | CanceledExecutionException | URISyntaxException | InvalidSettingsException ex) {
                ExceptionUtils.rethrow(ex);
            }
        });
        final var tmpFile = FileUtil.createTempFile("workflow-port-object", ".portobject", true);
        PortUtil.writeObjectToFile(poCopy, tmpFile, exec);
        return tmpFile;
    }

}
