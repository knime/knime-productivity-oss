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
 * The interface to an OSGi service / factor to create {@link IServerConnection}.
 * @author Bernd Wiswedel, KNIME, Konstanz, Germany
 */
@Deprecated(since = "4.7.1")
public interface KNIMEServerAwareConnectionService {

    /**
     * Create new connection for supported port object implementations (they represent KNIME server connection).
     * @param portObjectSpec server connection, if available
     * @param context the context the workflow runs in, needed for resolving relative paths etc.
     * @return An optional server connection to be used.
     * @throws InvalidSettingsException ...
     */
    public Optional<IServerConnection> createKNIMEServerConnection(final PortObjectSpec portObjectSpec,
        final WorkflowContext context) throws InvalidSettingsException;

    /**
     * Create a server connection for plain user,pass,path information.
     * @param hostAndPort url to the workflow
     * @param username username
     * @param password ...
     * @param connectTimeOut
     * @param readTimeout
     * @return The server connection
     */
    IServerConnection createKNIMEServerConnection(String hostAndPort, String username, String password,
        Duration connectTimeOut, Duration readTimeout);

    /** Extract error information, e.g. workflow not found, from a throwable. Used for error handling in the dialogs.
     * @param ex Exception itself
     * @return Extracted error text.
     */
    public Optional<String> handle(final Throwable ex);
}
