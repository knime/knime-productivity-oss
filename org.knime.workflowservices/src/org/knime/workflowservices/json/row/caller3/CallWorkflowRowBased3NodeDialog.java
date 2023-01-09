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
package org.knime.workflowservices.json.row.caller3;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.DialogComponentWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.CreateReportControls;

/**
 * Allows selecting a source column for each callee workflow input parameter, configuration of reporting, etc.
 *
 * Establishes an {@link IServerConnection} during {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])} to list
 * workflows and retrieve the input/output workflow parameters of the currently selected callee workflow.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
final class CallWorkflowRowBased3NodeDialog extends NodeDialogPane {

    /**
     * Used to list workflows available for execution and obtain {@link IWorkflowBackend} instances to control the
     * execution of callee workflows.
     *
     * Empty only if a problem occurs during {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])}, e.g., the
     * connected port object at the server connector port cannot be used create a connection via
     * {@link ServerConnectionUtil#getConnection(PortObjectSpec, org.knime.core.node.workflow.WorkflowManager)}
     */
    private IServerConnection m_serverConnection = null;

    /**
     * A JSON column in the input table can be selected as input for a callee workflow input parameter.
     *
     * Set on {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])}.
     */
    private DataTableSpec m_inputTableSpec;

    private final CallWorkflowRowBased3Configuration m_settings;

    private final Controls m_controls;

    private static final class Controls {
        private final DialogComponentWorkflowChooser m_workflowChooser;

        private final CallWorkflowConnectionControls m_connectionControls = new CallWorkflowConnectionControls();

        private final JPanel m_workflowPanel;

        private final JsonInputParametersPanel m_inputParameters = new JsonInputParametersPanel();

        private final CreateReportControls m_createReport;

        Controls(final SettingsModelWorkflowChooser settingsModelCalleeWorkflowChooser,
            final FlowVariableModel flowVariableModel) {

            m_workflowChooser = new DialogComponentWorkflowChooser(settingsModelCalleeWorkflowChooser,
                CallWorkflowUtil.WorkflowPathHistory.JSON_BASED_WORKFLOWS.getIdentifier(), flowVariableModel);

            m_workflowPanel = createWorkflowChooserPanel();

            // m_serverSettings needs to be created already
            m_createReport = new CreateReportControls(runReport -> {
                if (runReport.booleanValue()) {
                    m_connectionControls.forcePolling();
                } else {
                    m_connectionControls.enablePollingSelection();
                }
            });

        }

        private JPanel createWorkflowChooserPanel() {
            final var panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Workflow path"));
            final var gbc = createAndInitGBC();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
            panel.add(m_workflowChooser.getComponentPanel(), gbc);
            return panel;
        }

        private static final GridBagConstraints createAndInitGBC() {
            final var gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.weightx = 1;
            return gbc;
        }

        private JPanel createAdvancedTab() {
            final var container = new JPanel();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            container.add(m_connectionControls.getTimeoutControls().getPanel());
            container.add(m_connectionControls.getBackoffPanel());
            container.add(Box.createHorizontalGlue());
            return container;
        }

        /**
         * @return contains the main server connection settings (polling, job discard/keep), workflow path selector,
         *         input parameter mapping and report controls.
         */
        public JPanel createMainTab() {
            final var p = new JPanel();
            final var padding = 10;
            p.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

            p.add(m_connectionControls.getMainPanel());
            p.add(m_workflowPanel);
            p.add(m_inputParameters.getPanel());
            p.add(m_createReport.getPanel());

            return p;
        }

        /**
         * @param enabled
         */
        public void setEnabled(final boolean enabled) {
            // also controls backoff panel
            m_connectionControls.enableAllUIElements(enabled);
            m_inputParameters.getPanel().setEnabled(enabled);
            m_createReport.enableAllUIElements(enabled);
        }

    }

    CallWorkflowRowBased3NodeDialog(final CallWorkflowRowBased3Configuration callWorkflowRowBasedConfiguration) {
        m_settings = callWorkflowRowBasedConfiguration;

        final var flowVariableModel = createFlowVariableModel(
            m_settings.getWorkflowChooserModel().getKeysForFSLocation(), FSLocationVariableType.INSTANCE);
        m_controls = new Controls(m_settings.getWorkflowChooserModel(), flowVariableModel);

        m_settings.getWorkflowChooserModel()
            .addChangeListener(e -> this.fetchCalleeWorkflowParameters());

        addTab("Workflow", m_controls.createMainTab());
        addTab("Advanced Settings", m_controls.createAdvancedTab());
    }

    /**
     * Retrieve the callee workflow input parameters and pass them to the {@link Controls#m_inputParameters} panel to
     * allow binding data to the input parameters.
     */
    private void fetchCalleeWorkflowParameters() {

        var status = m_settings.getWorkflowChooserModel().getStatusMessage();
        if(status.getType() == StatusMessage.MessageType.ERROR) {
            NodeLogger.getLogger(getClass()).warn(status.getMessage());
            m_controls.m_inputParameters.setError(status.getMessage());
            return;
        }

        var workflowPathTrimmed = StringUtils.trim(m_settings.getWorkflowPath());
        // something went wrong during #loadSettingsFrom - this shouldn't be enabled anyways
        if (StringUtils.isEmpty(workflowPathTrimmed) || m_serverConnection == null) {
            return;
        }

        final var conf = new CallWorkflowConnectionConfiguration()//
            .setKeepFailingJobs(false) //
            .setSynchronousInvocation(true) //
            .setDiscardJobOnSuccessfulExecution(true) //
            .setBackoffPolicy(m_controls.m_connectionControls.getBackoffPanel().getSelectedBackoffPolicy()) //
            .setLoadTimeout(m_controls.m_connectionControls.getTimeoutControls().getSelectedLoadTimeout()) //
            .setFetchParametersTimeout(m_controls.m_connectionControls.getTimeoutControls().getSelectedFetchParametersTimeout())//
            .setWorkflowPath(workflowPathTrimmed);

        try (var backend = m_serverConnection.createWorkflowBackend(conf)) {
            backend.loadWorkflow();
            var inputNodes = backend.getInputNodes();
            m_controls.m_inputParameters.createPanels(inputNodes, m_inputTableSpec);
            m_controls.m_inputParameters.loadConfiguration(m_settings);
            m_controls.m_inputParameters.clearError();
        } catch (Exception ex) {
            var cause = (ex.getCause() != null) ? ExceptionUtils.getRootCause(ex) : ex;
            var message = "Cannot fetch workflow input and output parameters.\n" + ServerConnectionUtil.getService()
                .flatMap(s -> s.handle(cause)).orElse("Unknown reason (" + cause.getClass().getName() + ")");
            m_controls.m_inputParameters.setError(message);
            NodeLogger.getLogger(CallWorkflowRowBased3NodeDialog.class).debug(message, cause);
        }

        var panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        // apply GUI state to model state
        m_controls.m_connectionControls.saveToConfiguration(m_settings);
        m_controls.m_inputParameters.saveToConfiguration(m_settings);
        m_controls.m_createReport.saveToConfiguration(m_settings);
        // persist model state
        m_settings.saveSettings(settings);
        m_controls.m_workflowChooser.saveSettingsTo(settings);
    }

    /**
     * Load configuration and establish a server connection using the optional space connector at port 0. If no space
     * connector is present, create a local connection.
     *
     * Fetch the input and output parameters of the configured callee workflow.
     *
     * {@inheritDoc}
     *
     * @param inSpecs port 0 is an optional space connector. If nothing is connected, local workflows can be executed. If
     *            something is connected, the upstream must deliver a space connector that is fit for instantiating an
     *            {@link IServerConnection}. port 1 must provide a DataTableSpec. It is kept to find JSON input columns
     *            in {@link #onWorkflowPathChanged(String)}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs)
        throws NotConfigurableException {

        m_controls.m_workflowChooser.loadSettingsFrom(settings, inSpecs);
        m_settings.loadSettingsInDialog(settings);

        m_inputTableSpec = (DataTableSpec)inSpecs[CallWorkflowRowBased3NodeFactory.getDataPortIndex(m_settings)];

        // load settings
        m_controls.m_connectionControls.loadConfiguration(m_settings);
        m_controls.m_createReport.loadFromConfiguration(m_settings);

        var wfm = NodeContext.getContext().getWorkflowManager();
        final var connectionSpec = m_settings.isConnectorPresent() ? inSpecs[0] : null;

        // establish connection
        final var isRemoteExecution = m_settings.isConnectorPresent();

        initServerConnection(wfm, connectionSpec, isRemoteExecution);
        if (m_serverConnection != null) {
            m_controls.setEnabled(true);
            // disable timeouts and backoff controls if connection is local
            m_controls.m_connectionControls.setRemoteConnection(m_serverConnection, isRemoteExecution);
        } else {
            m_controls.setEnabled(false);
        }

        // fetch input and output parameters of callee workflow
        // creates input panels
        // reapply configured data source for each input parameter if possible
        try {
            fetchCalleeWorkflowParameters();
        } catch(IllegalStateException e) { //NOSONAR it is perfectly legal to not have chosen a workflow yet via the user interface
            // if no workflow has been chosen yet
        }
    }

    /**
     * @param wfm
     * @param connectionSpec
     * @param isRemoteExecution
     */
    private void initServerConnection(final WorkflowManager wfm, final PortObjectSpec connectionSpec,
        final boolean isRemoteExecution) {
        try {
            m_serverConnection = ServerConnectionUtil.getConnection(connectionSpec, wfm);
        } catch (InvalidSettingsException e) {
            getLogger().debug(e.getMessage(), e);
            if (isRemoteExecution) {
                m_controls.m_connectionControls
                    .setError("Please execute the KNIME Connector node that provides the remote connection.");
            } else {
                m_controls.m_connectionControls.setError(e.getMessage());
            }
            m_serverConnection = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        if (m_serverConnection != null) {
            try {
                m_serverConnection.close();
            } catch (IOException e) {
                NodeLogger.getLogger(getClass()).warn(e);
            }
            m_serverConnection = null;
        }
    }

}
