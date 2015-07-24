/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 17, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow;

import java.util.Map;

import javax.json.JsonValue;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;

/**
 * Interface to access a workflow. Can be either a local workflow or a remote flow (via REST calls then).
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
public interface IWorkflowBackend extends AutoCloseable {
    /** Wraps the workflow state - either translates to node container state (local) or the REST version. */
    public enum WorkflowState {
        IDLE, RUNNING, EXECUTED,
    }

    /**
     * Returns a map of the input nodes in the external workflow. The map keys are the unique input IDs.
     *
     * @return a map of input nodes
     */
    Map<String, ExternalNodeData> getInputNodes();

    /**
     * Sets the input nodes for the workflow. The map should have the same structure as the one returned by
     * {@link #getInputNodes()} but with potententially updated values.
     *
     * @param input a map with the updated input data
     * @throws InvalidSettingsException
     */
    void setInputNodes(Map<String, ExternalNodeData> input) throws InvalidSettingsException;

    /**
     * Returns a map with the output values of the called workflow. That map keys are the unique output IDs.
     *
     * @return a map between IDs and values
     */
    Map<String, JsonValue> getOutputValues();

    /**
     * Executes the workflow and returns the state after execution. The map doesn't need to contain all input values
     * but only the ones that have changed
     *
     * @param input a map with the updated input data
     * @return the current workflow state
     * @throws Exception if an error occurs during execution
     */
    WorkflowState execute(final Map<String, ExternalNodeData> input) throws Exception;

    /**
     * Returns the messages that occurred during execution.
     *
     * @return the messages or an empty string if there are no messages
     */
    String getWorkflowMessage();
}
