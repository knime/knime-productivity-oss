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
 *   Created on 13 Jan 2023 by Dionysios Stolis
 */
package org.knime.workflowservices.connection;

import java.util.Optional;

/**
 * An abstract factory to create connection instances for the KNIME Hub or KNIME Server or for local operations of other
 * workflows.
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 * @param <T> The remote or local access service.
 * @param <U> The connection configurations to be used by the factory implementation.
 *
 * @noreference this is interface is not intended to be used by users.
 */
public interface AbstractConnectionFactory<T, U> {

    /**
     * Creates a connection between KNIME Analytics Platform and KNIME Hub or KNIME Server, the connection is also used
     * for local execution service like {@link LocalExecutionConnection} for the Call Workflow Nodes.
     *
     * @param u the connection configuration.
     * @return a service to access the KNIME Hub, KNIME Server, or to operate with a workflow locally.
     */
    Optional<T> create(U u);
}
