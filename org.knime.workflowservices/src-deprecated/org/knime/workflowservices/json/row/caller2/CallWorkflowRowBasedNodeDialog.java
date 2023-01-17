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
package org.knime.workflowservices.json.row.caller2;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.NodeContext;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.CreateReportControls;
import org.knime.workflowservices.connection.util.SelectWorkflowPanel;

/**
 * Allows selecting a source for each callee workflow input parameter, configuration of reporting, etc.
 *
 * Establishes an {@link IServerConnection} during {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])} which is
 * stored in {@link #m_serverConnection} to list workflows and retrieve the input/output workflow parameters of the
 * currently selected callee workflow.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 *
 * @deprecated see {@link org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3NodeFactory}
 */
@Deprecated(since = "4.7.0")
final class CallWorkflowRowBasedNodeDialog extends NodeDialogPane {

    /**
     * Used to list workflows available for execution and obtain {@link IWorkflowBackend} instances to control the
     * execution of callee workflows.
     *
     * Empty only if a problem occurs during {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])}, e.g., the
     * connected port object at the server connector port cannot be used create a connection via
     * {@link ServerConnectionUtil#getConnection(PortObjectSpec, org.knime.core.node.workflow.WorkflowManager)}
     */
    private Optional<IServerConnection> m_serverConnection = Optional.empty();

    /**
     * A JSON column in the input table can be selected as input for a callee workflow input parameter.
     *
     * Set on {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[]).
     */
    private DataTableSpec m_inputTableSpec;

    private final CallWorkflowRowBasedConfiguration m_settings = new CallWorkflowRowBasedConfiguration();

    private final Controls m_controls;

    private final class Controls {

        private final CallWorkflowConnectionControls m_connection = new CallWorkflowConnectionControls();

        private final SelectWorkflowPanel m_workflowPanel;

        private final JsonInputParametersPanel m_inputParameters = new JsonInputParametersPanel();

        private final CreateReportControls m_createReport;

        Controls(final JPanel jPanel) {
            m_workflowPanel = new SelectWorkflowPanel(jPanel,
                CallWorkflowUtil.WorkflowPathHistory.JSON_BASED_WORKFLOWS.getIdentifier());

            // m_serverSettings needs to be created already
            m_createReport = new CreateReportControls(runReport -> {
                if (runReport) {
                    m_connection.forcePolling();
                } else {
                    m_connection.enablePollingSelection();
                }
            });

        }

        private JPanel createAdvancedTab() {
            final var container = new JPanel();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            container.add(m_connection.getTimeoutControls().getPanel());
            container.add(m_connection.getBackoffPanel());
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

            p.add(m_connection.getMainPanel());
            p.add(m_workflowPanel.getPanel());
            p.add(m_inputParameters.getPanel());
            p.add(m_createReport.getPanel());

            return p;
        }

        /**
         * @param enabled
         */
        public void setEnabled(final boolean enabled) {
            // also controls backoff panel
            m_connection.enableAllUIElements(enabled);
            m_workflowPanel.enableAllUIElements(enabled);
            m_inputParameters.getPanel().setEnabled(enabled);
            m_createReport.enableAllUIElements(enabled);
        }

    }

    CallWorkflowRowBasedNodeDialog() {
        m_controls = new Controls(getPanel());

        addTab("Workflow", m_controls.createMainTab());
        addTab("Advanced Settings", m_controls.createAdvancedTab());


        m_controls.m_workflowPanel.setListWorkflows(this::listWorkflows);
        m_controls.m_workflowPanel.setWorkflowPathChangedCallback(this::onWorkflowPathChanged);
    }

    /**
     * Retrieve the callee workflow input parameters and pass them to the {@link Controls#m_inputParameters} panel to
     * allow binding data to the input parameters.
     */
    private void onWorkflowPathChanged(final String workflowPath) {
        var workflowPathTrimmed = StringUtils.trim(workflowPath);
        // something went wrong during #loadSettingsFrom - this shouldn't be enabled anyways
        if (StringUtils.isEmpty(workflowPathTrimmed) || m_serverConnection.isEmpty()) {
            return;
        }

        final var conf = new CallWorkflowConnectionConfiguration() //
            .setKeepFailingJobs(false) //
            .setSynchronousInvocation(true) //
            .setDiscardJobOnSuccessfulExecution(true) //
            .setBackoffPolicy(m_controls.m_connection.getBackoffPanel().getSelectedBackoffPolicy()) //
            .setLoadTimeout(m_controls.m_connection.getTimeoutControls().getSelectedLoadTimeout()) //
            .setFetchParametersTimeout(m_controls.m_connection.getTimeoutControls().getSelectedFetchParametersTimeout())//
            .setWorkflowPath(workflowPathTrimmed);

        try (var backend = m_serverConnection.get().createWorkflowBackend(conf)) {
            var inputNodes = backend.getInputNodes();
            m_controls.m_inputParameters.createPanels(inputNodes, m_inputTableSpec);
            m_controls.m_inputParameters.loadConfiguration(m_settings);
            m_controls.m_inputParameters.clearError();
        } catch (Exception ex) {
            var cause = (ex.getCause() != null) ? ExceptionUtils.getRootCause(ex) : ex;
            var message = "Cannot fetch workflow input and output parameters.\n" + ServerConnectionUtil.getService()
                .flatMap(s -> s.handle(cause)).orElse("Unknown reason (" + cause.getClass().getName() + ")");
            m_controls.m_inputParameters.setError(message);
            NodeLogger.getLogger(CallWorkflowRowBasedNodeDialog.class).debug(message, cause);
        }

        var panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();
    }

    /**
     * Invoked by the browse workflows button in order to populate the browse workflows dialog.
     *
     * @return the paths/identifiers of the workflows in the connected space
     */
    private List<String> listWorkflows() {
        // something went wrong during load settings - should ideally disable workflow browsing
        if (m_serverConnection.isEmpty()) {
            return List.of();
        }
        try {
            return m_serverConnection.get().listWorkflows();
        } catch (ListWorkflowFailedException e) {
            getLogger().warn(e);
            return List.of();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        // apply GUI state to model state
        m_controls.m_connection.saveToConfiguration(m_settings);
        m_controls.m_workflowPanel.saveToConfiguration(m_settings);
        m_controls.m_inputParameters.saveToConfiguration(m_settings);
        m_controls.m_createReport.saveToConfiguration(m_settings);
        // persist model state
        m_settings.saveSettings(settings);
    }

    /**
     * Load configuration and establish a server connection using the optional space connector at port 0. If no space
     * connector is present, create a local connection.
     *
     * Fetch the input and output parameters of the configured callee workflow.
     *
     * {@inheritDoc}
     *
     * @param specs port 0 is an optional space connector. If nothing is connected, local workflows can be executed. If
     *            something is connected, the upstream must deliver a space connector that is fit for instantiating an
     *            {@link IServerConnection}. port 1 must provide a DataTableSpec. It is kept to find JSON input columns
     *            in {@link #onWorkflowPathChanged(String)}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {

        var spaceConnector = Optional.ofNullable(specs[0]);
        m_inputTableSpec = (DataTableSpec)specs[1];

        // load settings
        m_settings.loadSettingsInDialog(settings);
        m_controls.m_connection.loadConfiguration(m_settings);
        m_controls.m_workflowPanel.loadConfiguration(m_settings);
        m_controls.m_createReport.loadFromConfiguration(m_settings);

        // fail if this is a temporary copy of a workflow
        var wfm = NodeContext.getContext().getWorkflowManager();

        // establish connection
        try {
            m_serverConnection = Optional.of(ServerConnectionUtil.getConnection(specs[0], wfm));
            final var isRemoteExecution = spaceConnector.isPresent();
            m_controls.setEnabled(true);
            m_controls.m_connection.setRemoteConnection(m_serverConnection.get(), isRemoteExecution);
            m_controls.m_workflowPanel.setServerConnection(m_serverConnection.get());
        } catch (InvalidSettingsException e) {
            getLogger().debug(e.getMessage(), e);
            if (spaceConnector.isPresent()) {
                m_controls.m_connection
                    .setError("Please execute the KNIME Connector node that provides the remote connection.");
            } else {
                m_controls.m_connection.setError(e.getMessage());
            }
            m_serverConnection = Optional.empty();
            m_controls.setEnabled(false);
        }

        // fetch input and output parameters of callee workflow
        // creates input panels
        // reapply configured data source for each input parameter if possible
        onWorkflowPathChanged(m_settings.getWorkflowPath());
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        if (m_serverConnection.isPresent()) {
            try {
                m_serverConnection.get().close();
            } catch (IOException e) {
                NodeLogger.getLogger(getClass()).warn(e);
            }
            m_serverConnection = Optional.empty();
        }
    }

}
