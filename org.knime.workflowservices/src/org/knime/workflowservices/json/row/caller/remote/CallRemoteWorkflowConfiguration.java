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
 *   Created on Feb 16, 2015 by wiswedel
 */
package org.knime.workflowservices.json.row.caller.remote;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.productivity.base.callworkflow.CallWorkflowConfiguration;
import org.knime.workflowservices.connection.util.BackoffPolicy;

/**
 * Config object to node. Holds (remote) workflow URI and parameters.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
final class CallRemoteWorkflowConfiguration extends CallWorkflowConfiguration {
    private String m_remoteHostAndPort;

    private String m_username;

    private String m_password;

    private String m_credentialsName;

    private boolean m_synchronousInvocation;

    private Optional<Duration> m_loadTimeout = Optional.empty();

    private Optional<Duration> m_connectTimeout = Optional.empty();

    private Optional<Duration> m_readTimeout = Optional.empty();

    private Optional<BackoffPolicy> m_backoffPolicy = Optional.empty();

    /** @return the remoteHostAndPort */
    String getRemoteHostAndPort() {
        return m_remoteHostAndPort;
    }

    /** @param remoteHostAndPort the remoteHostAndPort to set */
    void setRemoteHostAndPort(final String remoteHostAndPort) {
        m_remoteHostAndPort = remoteHostAndPort;
    }

    /** @return the username */
    String getUsername() {
        return m_username;
    }

    /** @param username the username to set */
    void setUsername(final String username) {
        m_username = username;
    }

    /** @return the password */
    String getPassword() {
        return m_password;
    }

    /** @param password the password to set */
    void setPassword(final String password) {
        m_password = password;
    }

    /**
     * @param synchronousInvocation the synchronousInvocation to set
     */
    void setSynchronousInvocation(final boolean synchronousInvocation) {
        m_synchronousInvocation = synchronousInvocation;
    }

    /**
     * @return the synchronousInvocation
     */
    boolean isSynchronousInvocation() {
        return m_synchronousInvocation;
    }

    /**
     * Returns the name of the credentials that should be used or <code>null</code> if no credentials should be used.
     *
     * @return a credentials name or <code>null</code>
     */
    String getCredentialsName() {
        return m_credentialsName;
    }

    /**
     * Sets the name of the credentials that should be used or <code>null</code> if no credentials should be used.
     *
     * @param name a credentials name or <code>null</code>
     */
    void setCredentialsName(final String name) {
        m_credentialsName = name;
    }

    Optional<Duration> getLoadTimeout() {
        return m_loadTimeout;
    }

    Optional<Duration> getConnectTimeout() {
        return m_connectTimeout;
    }

    Optional<Duration> getReadTimeout() {
        return m_readTimeout;
    }

    void setLoadTimeout(final Duration duration) {
        m_loadTimeout = Optional.of(duration);
    }

    void setConnectTimeout(final Duration duration) {
        m_connectTimeout = Optional.of(duration);
    }

    void setReadTimeout(final Duration duration) {
        m_readTimeout = Optional.of(duration);
    }

    void setBackoffPolicy(final BackoffPolicy backoffPolicy) {
        m_backoffPolicy = Optional.of(backoffPolicy);
    }

    Optional<BackoffPolicy> getBackoffPolicy() {
        return m_backoffPolicy;
    }

    @Override
    public CallRemoteWorkflowConfiguration save(final NodeSettingsWO settings) {
        super.save(settings);
        m_loadTimeout.ifPresent(duration -> settings.addInt("loadTimeout", (int)duration.getSeconds()));
        m_connectTimeout.ifPresent(duration -> settings.addInt("connectTimeout", (int)duration.getSeconds()));
        m_readTimeout.ifPresent(duration -> settings.addInt("readTimeout", (int)duration.getSeconds()));
        m_backoffPolicy.ifPresent(backoffPolicy -> backoffPolicy.saveToSettings(settings));
        settings.addString("remoteHostAndPort", m_remoteHostAndPort);
        settings.addString("username", m_username);
        if (m_credentialsName == null) { // only call #addPassword when required, see AP-15442
            settings.addPassword("password", "GladYouFoundIt", m_password);
        }
        settings.addString("credentialsName", m_credentialsName);
        settings.addBoolean("synchronousInvocation", m_synchronousInvocation);
        return this;
    }

    @Override
    public CallRemoteWorkflowConfiguration loadInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        super.loadInModel(settings);
        m_loadTimeout = getTimeoutFromSettings(settings, "loadTimeout");
        m_connectTimeout = getTimeoutFromSettings(settings, "connectTimeout");
        m_readTimeout = getTimeoutFromSettings(settings, "readTimeout");
        m_backoffPolicy = BackoffPolicy.loadFromSettings(settings);
        m_remoteHostAndPort = settings.getString("remoteHostAndPort");
        m_credentialsName = settings.getString("credentialsName");
        m_username = settings.getString("username");
        m_password = m_credentialsName == null ? settings.getPassword("password", "GladYouFoundIt") : "";
        // added in 3.1.1
        m_synchronousInvocation = settings.getBoolean("synchronousInvocation", true);
        return this;
    }

    @Override
    public CallRemoteWorkflowConfiguration loadInDialog(final NodeSettingsRO settings) {
        super.loadInDialog(settings);
        m_loadTimeout = getTimeoutFromSettings(settings, "loadTimeout");
        m_connectTimeout = getTimeoutFromSettings(settings, "connectTimeout");
        m_readTimeout = getTimeoutFromSettings(settings, "readTimeout");
        m_backoffPolicy = BackoffPolicy.loadFromSettings(settings);
        m_remoteHostAndPort = settings.getString("remoteHostAndPort", null);
        m_remoteHostAndPort =
            StringUtils.defaultIfBlank(m_remoteHostAndPort, "http://localhost:8080/com.knime.enterprise.server/rest");
        m_username = settings.getString("username", System.getProperty("user.name"));
        m_password = settings.getPassword("password", "GladYouFoundIt", "");
        m_credentialsName = settings.getString("credentialsName", null);
        m_synchronousInvocation = settings.getBoolean("synchronousInvocation", true);
        return this;
    }

    private static Optional<Duration> getTimeoutFromSettings(final NodeSettingsRO settings, final String key) {
        try {
            return Optional.of(Duration.ofSeconds(Math.max(settings.getInt(key), 0)));
        } catch (InvalidSettingsException e) { // NOSONAR added in v4.3
            return Optional.empty();
        }
    }

}
