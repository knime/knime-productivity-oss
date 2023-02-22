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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.util.auth.Authenticator;

/**
 * To execute a workflow on the hub, the location must be specified (handled by the {@link FileSystemInvocationTarget}).
 * In addition, an execution context needs to be configured that provides the compute resources to carry out the ad hoc execution.
 * The authenticator is extracted from the file system connection.
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class HubAdHocConnectionConfiguration extends RemoteCallWorkflowConnectionConfiguration<FileSystemInvocationTarget> {

    /** Key under which the execution context id is persisted in node settings. */
    private static final String SETTINGS_KEY_EXECUTION_CONTEXT = "executionContext";

    private String m_executionContextId;

    private Authenticator m_authenticator;

    /**
     * @param invocationTarget
     */
    protected HubAdHocConnectionConfiguration(final FileSystemInvocationTarget invocationTarget) {
        super(invocationTarget);
    }

    @Override
    protected void saveSettings(final NodeSettingsWO settings) {
        super.saveSettings(settings);
        settings.addString(SETTINGS_KEY_EXECUTION_CONTEXT, m_executionContextId);
    }

    @Override
    protected void loadBaseSettings(final NodeSettingsRO settings, final boolean strict) throws InvalidSettingsException {
        super.loadBaseSettings(settings, strict);
        m_executionContextId = settings.getString(SETTINGS_KEY_EXECUTION_CONTEXT, "");
    }

    /**
     * @return the ID of the execution context to execute the workflow (invocation target)
     */
    public String getExecutionContext() {
        return m_executionContextId;
    }

    /**
     * Sets the execution context to be used to execute the workflow (invocation target)
     *
     * @param executionContext the ID of the execution context
     */
    public void setExecutionContext(final String executionContext) {
        m_executionContextId = executionContext;
    }

    /**
     * TODO
     *
     * @return the authenticator
     */
    public Authenticator getAuthneticator() {
        return m_authenticator;
    }

    /**
     * TODO
     *
     * @param authenticator the authenticator to set
     */
    public void setAuthneticator(final Authenticator authenticator) {
        m_authenticator = authenticator;
    }

}
