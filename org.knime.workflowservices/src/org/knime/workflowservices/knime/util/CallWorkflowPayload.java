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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.exec.dataexchange.PortObjectRepository;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;

/**
 * Represents the binary data that is set in the Workflow Input via an external call
 * ({@link org.knime.core.node.dialog.InputNode#setInputData(org.knime.core.node.dialog.ExternalNodeData)}. Since that
 * only represents a stream this class represents pre-processed data.
 *
 * @author wiswedel
 */
public interface CallWorkflowPayload extends Closeable {

    /**
     * Creates the actual payload object based on the selected port type.
     *
     * @param stream
     * @param portType
     * @return a {@link CallWorkflowPayload}
     * @throws IOException
     * @throws InvalidSettingsException
     */
    public static CallWorkflowPayload createFrom(final InputStream stream, final PortType portType)
        throws IOException, InvalidSettingsException {
        if (BufferedDataTable.TYPE.equals(portType)) {
            return TableCallWorkflowPayload.createFrom(stream);
        } else if (FlowVariablePortObject.TYPE.equals(portType)) {
            return FlowVariablesCallWorkflowPayload.createFrom(stream);
        } else {
            return PortObjectCallWorkflowPayload.createFrom(stream);
        }
    }

    /**
     * Creates a new port object or returns the existing one if no additional functionality should be applied to the
     * existing port object.
     *
     * @param exec an {@link ExecutionContext}
     * @param pushTo used to handle the flow variables
     * @param portObjRepoNodeModel a special kind of node model which receives the port objects possibly stored in a
     *            {@link WorkflowPortObject}; the node model manages them and makes them available through the
     *            {@link PortObjectRepository}
     * @return a {@link PortObject}
     * @throws Exception
     */
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo,
        AbstractPortObjectRepositoryNodeModel portObjRepoNodeModel) throws Exception;

    public PortObjectSpec getSpec();

}
