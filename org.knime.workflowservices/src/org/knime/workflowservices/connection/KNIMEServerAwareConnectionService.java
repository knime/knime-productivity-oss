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
 *   Created on Nov 15, 2021 by wiswedel
 */
package org.knime.workflowservices.connection;

import java.time.Duration;
import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowContext;

/**
 * TODO Javadoc
 *
 * @author wiswedel
 */
public interface KNIMEServerAwareConnectionService {

    public Optional<IServerConnection> createKNIMEServerConnection(final PortObjectSpec portObjectSpec,
        final WorkflowContext context) throws InvalidSettingsException;

    /**
     * @param hostAndPort
     * @param username
     * @param password
     * @param connectTimeOut
     * @param readTimeout
     * @return
     */
    IServerConnection createKNIMEServerConnection(String hostAndPort, String username, String password,
        Duration connectTimeOut, Duration readTimeout);

    public Optional<String> handle(final Throwable ex);
}
