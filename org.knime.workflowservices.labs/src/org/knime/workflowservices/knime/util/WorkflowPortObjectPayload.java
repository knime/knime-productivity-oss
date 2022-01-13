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
 *   Created on 11 Jan 2022 by Dionysios Stolis
 */
package org.knime.workflowservices.knime.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.capture.WorkflowPortObject;

/**
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public class WorkflowPortObjectPayload implements CallWorkflowPayload {

    private final WorkflowPortObject m_workflowPortObject;

    private WorkflowPortObjectPayload(final WorkflowPortObject workflowPortObject) {
        m_workflowPortObject = workflowPortObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (m_workflowPortObject instanceof Closeable) {
            ((Closeable)m_workflowPortObject).close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo) throws Exception {
        //TODO Add third parameter for the portObjectReferenceReaderNodes
        //TODO Uncomment the following functionality
        //        var segment = m_workflowPortObject.getSpec().getWorkflowSegment();
        //        var wfmCopy = segment.loadWorkflow();
        //        Set<NodeIDSuffix> portObjectReferenceReaderNodes = copyPortObjectReferenceReaderData(po, exec);
        //        var ws = new WorkflowSegment(wfmCopy, segment.getConnectedInputs(), segment.getConnectedOutputs(), portObjectReferenceReaderNodes);
        //        var spec = m_workflowPortObject.getSpec();
        //        return new WorkflowPortObject(new WorkflowPortObjectSpec(ws, spec.getWorkflowName(), spec.getInputIDs(), spec.getOutputIDs()));
        return m_workflowPortObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PortObjectSpec getSpec() {
        return m_workflowPortObject.getSpec();
    }

    static final WorkflowPortObjectPayload createFrom(final InputStream stream)
        throws IOException, InvalidSettingsException {
        try {
            WorkflowPortObject workflowPortObject =
                (WorkflowPortObject)PortUtil.readObjectFromStream(stream, new ExecutionMonitor());
            return new WorkflowPortObjectPayload(workflowPortObject);
        } catch (CanceledExecutionException e) {
            throw new InvalidSettingsException("Reading port object canceled", e);
        }
    }

    public WorkflowPortObject getWorkflowPortObject() {
        return m_workflowPortObject;
    }
}
