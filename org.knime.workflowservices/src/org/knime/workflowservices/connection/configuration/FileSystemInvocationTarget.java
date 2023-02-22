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

import java.net.URI;
import java.util.EnumSet;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.status.NodeModelStatusConsumer;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * The workflow to execute is located on a file system. This covers
 * <ul>
 * <li>local execution (the file system represents a mount point)</li>
 * <li>server execution (the file system represents the repository)</li>
 * <li>ad hoc execution on the hub (the file system represents the space)</li>
 * </ul>
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class FileSystemInvocationTarget extends ConnectorInvocationTarget {

    // ---- non-persisted fields -----

    private final NodeModelStatusConsumer m_statusConsumer;

    // ---- persisted fields -----

    /**
     * Manages the callee workflow path.
     *
     * @see #getWorkflowPath()
     */
    private SettingsModelWorkflowChooser m_workflowChooserModel;

    /**
     * @param creationConfig provides access to a node's ports
     * @param connectorPortGroupName the identifier of the port group that contains a single port that provides access to
     *            the invocation target, e.g., a file system connector port for workflows or a hub authenticator port
     *            for deployments
     */
    public FileSystemInvocationTarget(final NodeCreationConfiguration creationConfig, final String connectorPortGroupName) {
        super(creationConfig, connectorPortGroupName);

        m_workflowChooserModel =
            new SettingsModelWorkflowChooser("calleeWorkflow", connectorPortGroupName, m_portsConfiguration);
        m_statusConsumer = new NodeModelStatusConsumer(EnumSet.of(MessageType.ERROR, MessageType.WARNING));
    }

    /**
     * {@inheritDoc}
     * @throws InvalidSettingsException
     */
    @Override
    public void loadSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowChooserModel.loadSettingsFrom(settings);

    }

    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        m_workflowChooserModel.saveSettingsTo(settings);
    }

    @Override
    public void validate(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowChooserModel.validateSettings(settings);
    }

    @Override
    public void validate() throws InvalidSettingsException {
        // TODO Auto-generated method stub
    }

    /**
     * @return a plain string containing the path of the workflow to be called or a KNIME URI for local execution
     */
    public String getWorkflowPath() {
        // use uri only for
        // - mountpoint connector configured to "Current Mountpoint"
        // - mountpoint connector configured to other mount point "LOCAL"
        // - mountpoint connector configured to teamspace
        // - space connector configured to "Current Space"
        // - in case no connector is present
        if (ConnectionUtil.isRemoteConnection(m_workflowChooserModel.getLocation())) {
            return m_workflowChooserModel.getPath();
        } else {
            // the local workflow backend requires the path in a custom format, or knime uri
            return m_workflowChooserModel.getCalleeKnimeUri()//
                .map(URI::toString)//
                .orElse(m_workflowChooserModel.getPath());
        }
    }


    /**
     * Configures the callee workflow chooser settings model by passing the input port object specs to it.
     * @param specs
     * @throws InvalidSettingsException
     */
    public void configureCalleeModel(final PortObjectSpec[] specs) throws InvalidSettingsException {
        m_workflowChooserModel.configureInModel(specs, m_statusConsumer);
    }

    /**
     * @return the settings model for the callee workflow, i.e., the workflow to be executed by a Call Workflow node
     */
    public SettingsModelWorkflowChooser getWorkflowChooserModel() {
        return m_workflowChooserModel;
    }

    /**
     * @param workflowChooserModel sets the workflow chooser
     */
    public void setWorkflowChooserModel(final SettingsModelWorkflowChooser workflowChooserModel) {
         m_workflowChooserModel = workflowChooserModel;
    }

}