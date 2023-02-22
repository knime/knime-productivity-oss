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
 *   Created on May 24, 2018 by Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
package org.knime.workflowservices.knime.caller2;

import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.workflowservices.connection.configuration.CallWorkflowConnectionConfigurationFactory;
import org.knime.workflowservices.connection.configuration.DeploymentInvocationTarget;
import org.knime.workflowservices.connection.configuration.FileSystemInvocationTarget;
import org.knime.workflowservices.connection.configuration.LocalCallWorkflowConnectionConfiguration;
import org.knime.workflowservices.knime.caller.WorkflowParameters;

/**
 * Configuration for the Call Workflow Service node.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public final class CallWorkflow2NodeConfiguration2 {

    private static final String CFG_CALLEE_PROPERTIES = "calleeProperties";

    /**
     * The type parameter depends on the node ports.
     *
     * If a hub authentication port is added, the node will call a Deployment. In this case, the configuration will be
     * parameterized to a {@link DeploymentInvocationTarget}.
     *
     * If no port or a file system port is present in the connector port group, the type parameter will be bound to a
     * {@link FileSystemInvocationTarget}.
     */
    private final LocalCallWorkflowConnectionConfiguration<?> m_connectionConfiguration;

    /**
     * Stores information such as the inputs and outputs of the workflow referenced by the workflow path.
     */
    private Optional<WorkflowParameters> m_calleeWorkflowProperties = Optional.empty();

    /**
     * @param ncc provides the configured ports of the node
     * @param inputPortGroupName the name of the input port group in the {@link NodeCreationConfiguration} that contains
     *            the file system connection port if configured by the user
     */
    public CallWorkflow2NodeConfiguration2(final NodeCreationConfiguration ncc, final String inputPortGroupName) {
        m_connectionConfiguration = CallWorkflowConnectionConfigurationFactory.create(ncc, inputPortGroupName);
    }

    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        super.saveSettings(settings);
        m_calleeWorkflowProperties.ifPresent(c -> {
            var workflowPropSettings = settings.addNodeSettings(CFG_CALLEE_PROPERTIES);
            c.saveTo(workflowPropSettings);
        });
    }

    @Override
    public void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        super.loadSettingsInModel(settings);
        loadCalleeWorkflowProperties(settings);
    }

    @Override
    public void loadSettingsInDialog(final NodeSettingsRO settings) throws NotConfigurableException {
        super.loadSettingsInDialog(settings);
        loadCalleeWorkflowProperties(settings);
    }

    /**
     * @param settings
     * @throws InvalidSettingsException
     */
    private void loadCalleeWorkflowProperties(final NodeSettingsRO settings) {
        WorkflowParameters calleeWorkflowProperties = null;
        try {
            calleeWorkflowProperties = WorkflowParameters.loadFrom(settings.getNodeSettings(CFG_CALLEE_PROPERTIES));
        } catch (InvalidSettingsException e) {
        }
        m_calleeWorkflowProperties = Optional.ofNullable(calleeWorkflowProperties);
    }

    /**
     * @return the properties of the workflow selected for execution when the dialog was last closed
     */
    public Optional<WorkflowParameters> getCalleeWorkflowProperties() {
        return m_calleeWorkflowProperties;
    }

    /**
     * @param remoteWorkflowProperties null for no properties
     */
    public void setCalleeWorkflowProperties(final WorkflowParameters remoteWorkflowProperties) {
        m_calleeWorkflowProperties = Optional.ofNullable(remoteWorkflowProperties);
    }

}
