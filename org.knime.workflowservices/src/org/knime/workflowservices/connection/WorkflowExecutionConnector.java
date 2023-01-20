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
 *   Created on 16 Jan 2023 by Dionysios Stolis
 */
package org.knime.workflowservices.connection;

import java.io.IOException;
import java.util.List;

import org.knime.workflowservices.ExecutionContextSelector.ExecutionContextItem;
import org.knime.workflowservices.IWorkflowBackend;

/**
 * Provides access to the KNIME Hub (or Server) execution REST API for the remote Call Workflow operations. The workflow
 * can also be executed locally.
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public interface WorkflowExecutionConnector {

    /**
     * Creates a workflow execution service instance to access the corresponding back-end either remote (Hub or KNIME Server) or local.
     *
     * @return the workflow execution service implementation.
     * @throws IOException
     */
    IWorkflowBackend createWorkflowBackend() throws IOException;

    /**
     * Returns the execution contexts of the connected space in case it is connected to a Hub. Otherwise it'll return an
     * empty list.
     *
     * @return the execution contexts
     * @throws IOException if an error occurs while fetching execution contexts
     */
    public default List<ExecutionContextItem> getExecutionContexts() throws IOException {
        return List.of();
    }

}
