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
 *   Created on 10 Feb 2023 by Dionysios Stolis
 */
package org.knime.workflowservices.connection;

import java.io.IOException;
import java.util.List;

import org.knime.workflowservices.Deployment;

/**
 * Provides access to the KNIME Hub deployment execution REST API for remote Call Workflow operations.
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public interface DeploymentExecutionConnector extends WorkflowExecutionConnector {

    /**
     * List the REST and shared deployments of each team that a user has access to.
     *
     * @return a list of the REST and shared deployments.
     * @throws IOException if an error occur in the REST calls.
     */
    List<Deployment> getServiceDeployments() throws IOException, InterruptedException;
}
