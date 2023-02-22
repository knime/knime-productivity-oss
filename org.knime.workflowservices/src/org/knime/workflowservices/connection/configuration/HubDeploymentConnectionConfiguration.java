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

import org.knime.core.util.auth.Authenticator;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObject;

/**
 * To execute a deployed workflow on the hub, only the deployment id is required, which is managed by the invocation target.
 * The authenticator is retrieved from a {@link AbstractHubAuthenticationPortObject}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 *
 * @noreference This class is not intended to be referenced by clients.
 */
public class HubDeploymentConnectionConfiguration extends RemoteCallWorkflowConnectionConfiguration<DeploymentInvocationTarget> {

    private Authenticator m_authenticator;

    /**
     * @param invocationTarget the workflow or deployment to execute
     */
    protected HubDeploymentConnectionConfiguration(final DeploymentInvocationTarget invocationTarget) {
        super(invocationTarget);
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
