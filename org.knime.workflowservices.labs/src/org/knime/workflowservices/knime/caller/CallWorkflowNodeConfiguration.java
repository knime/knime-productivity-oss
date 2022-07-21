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
package org.knime.workflowservices.knime.caller;

import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;

/**
 * Configuration for the Call Workflow node.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
final class CallWorkflowNodeConfiguration extends CallWorkflowConnectionConfiguration {

    private static final String CFG_CALLEE_PROPERTIES = "calleeProperties";

    /**
     * Stores information such as the inputs and outputs of the workflow referenced by the workflow path.
     */
    private Optional<WorkflowParameters> m_calleeWorkflowProperties = Optional.empty();

    @Override
    protected void saveSettings(final NodeSettingsWO settings) {
        super.saveSettings(settings);
        m_calleeWorkflowProperties.ifPresent(c -> {
            var workflowPropSettings = settings.addNodeSettings(CFG_CALLEE_PROPERTIES);
            c.saveTo(workflowPropSettings);
        });
    }

    @Override
    protected void loadSettingsInModel(final NodeSettingsRO settings, final IServerConnection connection)
        throws InvalidSettingsException {
        super.loadSettingsInModel(settings, connection);
        loadCalleeWorkflowProperties(settings);
    }

    @Override
    protected void loadSettingsInDialog(final NodeSettingsRO settings) throws NotConfigurableException {
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
    Optional<WorkflowParameters> getCalleeWorkflowProperties() {
        return m_calleeWorkflowProperties;
    }

    /**
     * @param remoteWorkflowProperties null for no properties
     */
    void setCalleeWorkflowProperties(final WorkflowParameters remoteWorkflowProperties) {
        m_calleeWorkflowProperties = Optional.ofNullable(remoteWorkflowProperties);
    }

}
