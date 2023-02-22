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
import org.knime.core.node.context.NodeCreationConfiguration;

/**
 * A deployed workflow on any KNIME Hub instance.
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class DeploymentInvocationTarget extends ConnectorInvocationTarget {

    private static final String SETTINGS_KEY_DEPLOYMENT_ID = "deploymentId";

    /** Identifies the deployment to be called. */
    String m_deploymentId;

    final boolean m_strict;

    /**
     * @param deploymentId
     * @param strict
     * @param creationConfig
     * @param connectorPortGroupName
     */
    public DeploymentInvocationTarget(final String deploymentId, final boolean strict, final NodeCreationConfiguration creationConfig, final String connectorPortGroupName) {
        super(creationConfig, connectorPortGroupName);
        m_deploymentId = deploymentId;
        m_strict = strict;
    }

    /**
     * {@inheritDoc}
     *
     * @throws InvalidSettingsException
     */
    @Override
    public void loadSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_deploymentId = m_strict ? //
            settings.getString(SETTINGS_KEY_DEPLOYMENT_ID) : //
            settings.getString(SETTINGS_KEY_DEPLOYMENT_ID, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        settings.addString(SETTINGS_KEY_DEPLOYMENT_ID, getDeploymentId());
    }

    /**
     * @return the identifier of the deployment to execute
     */
    public String getDeploymentId() {
        return m_deploymentId;
    }

    @Override
    public void validate() throws InvalidSettingsException {
    }

    @Override
    public void validate(final NodeSettingsRO settings) throws InvalidSettingsException {
        // TODO Auto-generated method stub

    }

}