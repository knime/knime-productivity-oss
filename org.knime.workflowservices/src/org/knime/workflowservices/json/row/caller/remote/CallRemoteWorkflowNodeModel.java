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
 *   Created on 22.07.2015 by thor
 */
package org.knime.workflowservices.json.row.caller.remote;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.ICredentials;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.json.row.caller.CallWorkflowConfiguration;
import org.knime.workflowservices.json.row.caller.CallWorkflowNodeModel;

/**
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
class CallRemoteWorkflowNodeModel extends CallWorkflowNodeModel {
    private CallRemoteWorkflowConfiguration m_configuration = new CallRemoteWorkflowConfiguration();

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotBlank(m_configuration.getRemoteHostAndPort()),
            "Host must not be empty");

        if (m_configuration.getCredentialsName() != null) {
            ICredentials creds = getCredentialsProvider().get(m_configuration.getCredentialsName());
            CheckUtils.checkSettingNotNull(creds, "No credentials with name '%s' found.",
                m_configuration.getCredentialsName());
        } else {
            CheckUtils.checkSetting(StringUtils.isNotBlank(m_configuration.getUsername()), "User must not be empty");
        }

        return super.configure(inSpecs);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("resource")
    @Override
    protected IWorkflowBackend newBackend(final String workflowPath) throws Exception {
        final var hostAndPort = m_configuration.getRemoteHostAndPort();

        String username;
        String password;
        if (m_configuration.getCredentialsName() != null) {
            ICredentials creds = getCredentialsProvider().get(m_configuration.getCredentialsName());
            username = creds.getLogin();
            password = creds.getPassword();
        } else {
            username = m_configuration.getUsername();
            password = m_configuration.getPassword();
        }
        var sync = m_configuration.isSynchronousInvocation() && m_configuration.getReportFormatOrNull() == null;
        var loadTimeout = m_configuration.getLoadTimeout().orElse(IServerConnection.DEFAULT_LOAD_TIMEOUT);
        var connectTimeout = m_configuration.getConnectTimeout().orElse(IServerConnection.DEFAULT_TIMEOUT);
        var readTimeout = m_configuration.getReadTimeout().orElse(IServerConnection.DEFAULT_TIMEOUT);
        var backoffPolicy =
            m_configuration.getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY);

        var serverConnection =
            ServerConnectionUtil.getConnection(hostAndPort, username, password, connectTimeout, readTimeout);
        final var callConfig = new CallWorkflowConnectionConfiguration() //
                .setBackoffPolicy(backoffPolicy) //
                .setDiscardJobOnSuccessfulExecution(true) // retaining from old impl, see RemoteWorkflowBackend.Builder
                .setKeepFailingJobs(false) // default, retaining from old implementation
                .setLoadTimeout(loadTimeout) //
                .setSynchronousInvocation(sync) //
                .setWorkflowPath(workflowPath);
        return serverConnection.createWorkflowBackend(callConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CallWorkflowConfiguration getConfiguration() {
        return m_configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_configuration.save(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new CallRemoteWorkflowConfiguration().loadInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.loadInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }
}
