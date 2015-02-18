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

import javax.json.JsonObject;

/**
 * Interface to access a workflow. Can be either a local workflow or a remote flow (via REST calls then).
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
interface IWorkflowBackend {

    /** Wraps the workflow state - either translates to node container state (local) or the REST version. */
    public enum WorkflowState {
        IDLE,
        RUNNING,
        EXECUTED,
    }

    public Map<String, JsonObject> getInputNodes();

    public void setInputNodes(Map<String, JsonObject> input);

    public Map<String, JsonObject> getOutputNodes();

    public WorkflowState execute();

    public void discard();

}
