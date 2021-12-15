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
 *   Created on Nov 10, 2020 by wiswedel
 */
package org.knime.workflowservices.json.caller;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.Credentials;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;

import com.knime.enterprise.utility.PermissionException;
import com.knime.server.nodes.callworkflow.RemoteWorkflowBackend;
import com.knime.server.nodes.callworkflow.RemoteWorkflowBackend.Lookup;
import com.knime.server.nodes.callworkflow.RemoteWorkflowSupplier;
import com.knime.server.nodes.util.KnimeServerConnectionInformation;

/**
 * A server connection built from {@link KnimeServerConnectionInformation}, i.e. the old file handling nodes. This was
 * the only option until KNIME AP 4.3 (Dec 2020). The corresponding "KNIME Server Connection" node got deprecated with
 * 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @deprecated
 */
@Deprecated
final class Pre43ServerConnection implements IServerConnection { // NOSONAR

    private final KnimeServerConnectionInformation m_connectionInformation;

    Pre43ServerConnection(final KnimeServerConnectionInformation connectionInformation) {
        m_connectionInformation = CheckUtils.checkArgumentNotNull(connectionInformation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s:%s@%s", //
            m_connectionInformation.getUser(),
            m_connectionInformation.getPassword() == null ? "<no password>" : "xxxxx", //
            m_connectionInformation.getAbsoluteRestPath());
    }

    @Override
    public IWorkflowBackend createWorkflowBackend(final CallWorkflowConnectionConfiguration configuration)
        throws Exception {
        String path = m_connectionInformation.getAbsoluteRestPath();
        String username = m_connectionInformation.getUser();
        String password = m_connectionInformation.getPassword();
        return RemoteWorkflowBackend.newInstance( //
            Lookup.newLookup(path, configuration.getWorkflowPath(), username, password), //
            configuration.isSynchronousInvocation(), //
            configuration.getFailingJobPolicy(), //
            configuration.getSuccessfulJobPolicy(), //
            configuration.getLoadTimeout().orElse(DEFAULT_LOAD_TIMEOUT), //
            DEFAULT_TIMEOUT, //
            DEFAULT_TIMEOUT //
        );
    }

    @Override
    public String getHost() {
        return m_connectionInformation.getHost();
    }

    @Override
    public List<String> listWorkflows() throws ListWorkflowFailedException {
        String name = m_connectionInformation.getUser();
        String login = m_connectionInformation.getUser();
        String password = m_connectionInformation.getPassword();
        String restPath = m_connectionInformation.getAbsoluteRestPath();
        try {
            return RemoteWorkflowSupplier.listWorkflows(restPath, new Credentials(name, login, password));
        } catch (InstantiationException | IllegalAccessException | IOException | PermissionException
                | URISyntaxException e) {
            throw new ListWorkflowFailedException(e);
        }
    }

}
