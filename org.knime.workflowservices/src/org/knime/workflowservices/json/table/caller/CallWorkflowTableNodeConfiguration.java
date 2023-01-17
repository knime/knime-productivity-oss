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
package org.knime.workflowservices.json.table.caller;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.json.table.caller2.CallWorkflowTable2NodeFactory;

/**
 * Configuration for the Call Workflow (Table) node. Used by the deprecated nodes as well as
 * {@link CallWorkflowTable2NodeFactory}.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
public final class CallWorkflowTableNodeConfiguration extends CallWorkflowConnectionConfiguration {

    private String m_selectedInputParameter;

    private String m_selectedOutputParameter;

    private String m_flowVariableDestination;

    private String m_flowCredentialsDestination;

    private boolean m_useFullyQualifiedIds;

    /**
     * @deprecated use {@link #CallWorkflowTableNodeConfiguration(NodeCreationConfiguration, String)}
     */
    @Deprecated(since = "4.7.0")
    public CallWorkflowTableNodeConfiguration() {

    }

    /**
     * @param nodeCreationConfiguration see super constructor
     * @param portGroupName see super constructor
     * @see CallWorkflowConnectionConfiguration#CallWorkflowConnectionConfiguration(NodeCreationConfiguration, String)
     */
    public CallWorkflowTableNodeConfiguration(final NodeCreationConfiguration nodeCreationConfiguration, final String portGroupName) {
        super(nodeCreationConfiguration, portGroupName);
    }

    /**
     * Saves the configuration to the node settings.
     *
     * @param settings settings to which the configuration should be saved
     * @return this configuration
     */
    public CallWorkflowTableNodeConfiguration save(final NodeSettingsWO settings) {
        saveSettings(settings);

        if (m_selectedInputParameter != null) {
            settings.addString("selectedInputParameter", m_selectedInputParameter);
        }

        if (m_selectedOutputParameter != null) {
            settings.addString("selectedOutputParameter", m_selectedOutputParameter);
        }

        if (m_flowVariableDestination != null) {
            settings.addString("flowVariableDestination", m_flowVariableDestination);
        }

        if (m_flowCredentialsDestination != null) {
            settings.addString("flowCredentialsDestination", m_flowCredentialsDestination);
        }

        settings.addBoolean("useFullyQualifiedIds", m_useFullyQualifiedIds);

        return this;
    }

    /**
     * Loads the node settings in the node model.
     *
     * @param settings settings to be loaded
     * @return this configuration
     * @throws InvalidSettingsException
     */
    CallWorkflowTableNodeConfiguration loadInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        loadSettingsInModel(settings);
        m_selectedInputParameter = settings.getString("selectedInputParameter", "");
        m_selectedOutputParameter = settings.getString("selectedOutputParameter", "");
        m_flowVariableDestination = settings.getString("flowVariableDestination", "");
        m_flowCredentialsDestination = settings.getString("flowCredentialsDestination", "");
        m_useFullyQualifiedIds = settings.getBoolean("useFullyQualifiedIds");
        return this;
    }

    /**
     * Loads the node settings in the node dialog.
     *
     * @param settings settings to be loaded
     * @return this configuration
     * @throws InvalidSettingsException
     * @throws NotConfigurableException
     */
    public CallWorkflowTableNodeConfiguration loadInDialog(final NodeSettingsRO settings) throws NotConfigurableException {
        loadSettingsInDialog(settings);
        m_selectedInputParameter = settings.getString("selectedInputParameter", "");
        m_selectedOutputParameter = settings.getString("selectedOutputParameter", "");
        m_flowVariableDestination = settings.getString("flowVariableDestination", "");
        m_flowCredentialsDestination = settings.getString("flowCredentialsDestination", "");
        m_useFullyQualifiedIds = settings.getBoolean("useFullyQualifiedIds", false);
        return this;
    }

    /**
     * Sets the parameter to which the input table should be assigned to.
     *
     * @param inputParameter the parameter
     */
    public void setSelectedInputParameter(final String inputParameter) {
        m_selectedInputParameter = inputParameter;
    }

    /**
     * Returns the parameter the input table is assigned to.
     *
     * @return the parameter the input table is assigned to
     */
    public String getSelectedInputParameter() {
        return m_selectedInputParameter;
    }

    /**
     * Sets the parameter from which the output table should filled from.
     *
     * @param selectedOutputParameter the parameter
     */
    public void setSelectedOutputParameter(final String selectedOutputParameter) {
        m_selectedOutputParameter = selectedOutputParameter;
    }

    /**
     * Returns the parameter the output table is filled from.
     *
     * @return the parameter the output table is filled from
     */
    public String getSelectedOutputParameter() {
        return m_selectedOutputParameter;
    }

    /**
     * Returns the flow variable destination.
     *
     * @return the flowVariableDestination
     */
    public String getFlowVariableDestination() {
        return m_flowVariableDestination;
    }

    /**
     * Sets the flow variable destination.
     *
     * @param flowVariableDestination the flowVariableDestination to set
     */
    public void setFlowVariableDestination(final String flowVariableDestination) {
        m_flowVariableDestination = flowVariableDestination;
    }

    /**
     * Returns the flow credentials destination.
     *
     * @return the flowCredentialsDestination
     */
    public String getFlowCredentialsDestination() {
        return m_flowCredentialsDestination;
    }

    /**
     * Sets the flow credentials destination.
     *
     * @param flowCredentialsDestination the flowCredentialsDestination to set
     */
    public void setFlowCredentialsDestination(final String flowCredentialsDestination) {
        m_flowCredentialsDestination = flowCredentialsDestination;
    }

    /**
     * Sets the use fully qualified id choice.
     *
     * @param useFullyQualifiedIds the choice made
     */
    public void setUseQualifiedId(final boolean useFullyQualifiedIds) {
        m_useFullyQualifiedIds = useFullyQualifiedIds;
    }

    /**
     * Returns the user setting to use fully qualified id or not.
     *
     * @return the user setting to use fully qualified id or not
     */
    public boolean isUseFullyQualifiedId() {
        return m_useFullyQualifiedIds;
    }

}
