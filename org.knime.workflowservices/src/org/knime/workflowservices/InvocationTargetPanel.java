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
 *   Created on 14 Feb 2023 by Dionysios Stolis
 */
package org.knime.workflowservices;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.util.hub.ItemVersionStringPersistor;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.DialogComponentWorkflowChooser;
import org.knime.filehandling.core.util.GBCBuilder;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObjectSpec;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;

/**
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class InvocationTargetPanel {

    private DialogComponentWorkflowChooser m_workflowChooser;

    private final CalleeVersionSelectionPanel m_versionSelector;

    private final ExecutionContextSelector m_executionContextSelector;

    private final DeploymentSelectionPanel m_deploymentSelector;

    private final CallWorkflowConnectionConfiguration m_configuration;

    private final NodeDialogPane m_pane;

    /**
     * @param configuration
     * @param pane
     */
    public InvocationTargetPanel(final CallWorkflowConnectionConfiguration configuration, final NodeDialogPane pane) {
        m_configuration = configuration;
        m_pane = pane;
        m_versionSelector = new CalleeVersionSelectionPanel(ItemVersionStringPersistor.createFlowVariableModel(pane));
        m_executionContextSelector = new ExecutionContextSelector();
        m_deploymentSelector = new DeploymentSelectionPanel(configuration,
            pane.createFlowVariableModel("deploymentId", VariableType.StringType.INSTANCE));
    }

    /**
     * @return main panel containing all controls
     */
    public Component createExecutionPanel() {
        var executionPanel = new JPanel();
        executionPanel.setBorder(BorderFactory.createTitledBorder("Execution Target"));
        executionPanel.setLayout(new GridBagLayout());
        final GBCBuilder gbc = new GBCBuilder().resetPos().anchorLineStart()//
                .ipadX(5).ipadY(5)//
                .weight(1, 0).fillHorizontal();

        if (m_configuration.getConnectionType() == ConnectionType.FILE_SYSTEM) {
            executionPanel.add(createWorkflowSelectionPanel(), gbc.build());
            gbc.incY();
            executionPanel.add(m_versionSelector.getPanel(), gbc.build());
            gbc.incY();
            executionPanel.add(m_executionContextSelector.createSelectionPanel(), gbc.build());
        } else {
            executionPanel.add(m_deploymentSelector.getPanel(), gbc.build());
        }

        return executionPanel;
    }

    /**
     * Every time the selected deployment changes, the listener is called.
     *
     * @param changeListener
     */
    public void addDeploymentChangedListener(final Consumer<Deployment> changeListener) {
            m_deploymentSelector.addDeploymentChangedListener(changeListener);
    }

    private JComponent createWorkflowSelectionPanel() {
        m_workflowChooser =
            new DialogComponentWorkflowChooser(m_configuration.getWorkflowChooserModel(), "calleeWorkflow",
                m_pane.createFlowVariableModel(m_configuration.getWorkflowChooserModel().getKeysForFSLocation(),
                    FSLocationVariableType.INSTANCE));
        return m_workflowChooser.getComponentPanel();
    }

    /**
     * This loads the workflow chooser for file system based workflow selection.
     *
     * <ol>
     * <li>The dialog component ("chooser") requires to be loaded before
     * {@link CallWorkflowConnectionConfiguration#getWorkflowChooserModel()}.</li>
     * <li>The {@link CallWorkflowConnectionConfiguration} needs to be loaded before this panel can be loaded with
     * {@link #loadSettingsInDialog(CallWorkflowConnectionConfiguration, NodeSettingsRO, PortObjectSpec[])}.</li>
     * </ol>
     *
     * In order to satisfy loading order, loading the workflow chooser dialog component is factored out into this
     * method.
     *
     * @param settings
     * @param inSpecs
     * @throws NotConfigurableException
     */
    public void loadChooser(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs) throws NotConfigurableException {
        if (m_configuration.getConnectionType() == ConnectionType.FILE_SYSTEM) {
            m_workflowChooser.loadSettingsFrom(settings, inSpecs);
        }
    }

    /**
     * Call {@link #loadChooser(NodeSettingsRO, PortObjectSpec[])} before calling this.
     *
     * @param configuration
     * @param settings
     * @param specs
     * @throws NotConfigurableException
     */
    public void loadSettingsInDialog(final CallWorkflowConnectionConfiguration configuration,
        final NodeSettingsRO settings, final PortObjectSpec[] specs) throws NotConfigurableException {
        if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
            m_deploymentSelector.loadSettingsInDialog(configuration, settings);
            if (specs.length > 0 && specs[0] instanceof AbstractHubAuthenticationPortObjectSpec hubAuthPortObjectSpec) {
                m_configuration.setHubAuthentication(hubAuthPortObjectSpec);
            } else {
                throw new NotConfigurableException("Node is not connected to the Hub.");
            }
        } else {
            // workflow location is loaded in #loadChooser
            m_versionSelector.set(configuration.getItemVersion());
            m_executionContextSelector.loadSettingsInDialog(configuration);
        }
    }

    /**
     * @param settings
     * @param configuration
     * @throws InvalidSettingsException
     */
    public void saveSettingsTo(final NodeSettingsWO settings, final CallWorkflowConnectionConfiguration configuration)
        throws InvalidSettingsException {
        if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
            m_deploymentSelector.saveToConfiguration(configuration);
        } else {
            m_workflowChooser.saveSettingsTo(settings);
            m_executionContextSelector.saveToConfiguration(configuration);
        }
    }

    /**
     * Cancel all workers.
     */
    public void close() {
        m_executionContextSelector.close();
        m_deploymentSelector.close();
        if (m_workflowChooser != null) {
            m_workflowChooser.onClose();
        }
    }

    /**
     * @return the component used to display and manipulate the callee hub repository item version.
     */
    public CalleeVersionSelectionPanel getVersionSelector() {
        return m_versionSelector;
    }

    /**
     * @return
     */
    public InvocationTargetProvider<String> getDeploymentSelector() {
        return m_deploymentSelector;
    }
}
