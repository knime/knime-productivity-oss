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
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

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
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.data.location.variable.FSLocationVariableType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.DialogComponentWorkflowChooser;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.workflowservices.ExecutionContextSelector;
import org.knime.workflowservices.caller.util.CallWorkflowUtil;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil;
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
     * A JSON column in the input table can be selected as input for a callee workflow input parameter.
     *
     * Set on {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])}.
     */
    private DataTableSpec m_inputTableSpec;

    private final CallWorkflowRowBased3Configuration m_configuration;

    private final Controls m_controls;

    private static final class Controls {
        private final DialogComponentWorkflowChooser m_workflowChooser;

        private final ExecutionContextSelector m_executionContextSelector;

        private final CallWorkflowConnectionControls m_connectionControls = new CallWorkflowConnectionControls();

        private final JPanel m_workflowPanel;

        private final JsonInputParametersPanel m_inputParameters = new JsonInputParametersPanel();

        private final CreateReportControls m_createReport;

        Controls(final SettingsModelWorkflowChooser settingsModelCalleeWorkflowChooser,
            final FlowVariableModel flowVariableModel) {

            m_workflowChooser = new DialogComponentWorkflowChooser(settingsModelCalleeWorkflowChooser,
                CallWorkflowUtil.WorkflowPathHistory.JSON_BASED_WORKFLOWS.getIdentifier(), flowVariableModel);

            m_workflowPanel = createWorkflowChooserPanel();
            m_executionContextSelector = new ExecutionContextSelector();

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
            p.add(m_executionContextSelector.createSelectionPanel());
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
        m_configuration = callWorkflowRowBasedConfiguration;

        final var flowVariableModel = createFlowVariableModel(
            m_configuration.getWorkflowChooserModel().getKeysForFSLocation(), FSLocationVariableType.INSTANCE);
        m_controls = new Controls(m_configuration.getWorkflowChooserModel(), flowVariableModel);

        m_configuration.getWorkflowChooserModel().addChangeListener(e -> fetchCalleeWorkflowParameters());

        addTab("Workflow", m_controls.createMainTab());
        addTab("Advanced Settings", m_controls.createAdvancedTab());
    }

    /**
     * Retrieve the callee workflow input parameters and pass them to the {@link Controls#m_inputParameters} panel to
     * allow binding data to the input parameters.
     * @throws InvalidSettingsException
     */
    private void fetchCalleeWorkflowParameters() {
        var worker = new ParameterUpdateWorker(m_configuration, this::onWorkflowParametersLoad, this::onFailure);
        worker.execute();
    }

    private void onWorkflowParametersLoad(final Map<String, ExternalNodeData> inputNodes) {
        m_controls.m_inputParameters.createPanels(inputNodes, m_inputTableSpec);
        m_controls.m_inputParameters.loadConfiguration(m_configuration);
        m_controls.m_inputParameters.clearError();

        var panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();
    }

    private void onFailure(final String message) {
        m_controls.m_inputParameters.setError(message);
        NodeLogger.getLogger(CallWorkflowRowBased3NodeDialog.class).debug(message);
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        // apply GUI state to model state
        m_controls.m_connectionControls.saveToConfiguration(m_configuration);
        m_controls.m_inputParameters.saveToConfiguration(m_configuration);
        m_controls.m_createReport.saveToConfiguration(m_configuration);
        m_controls.m_executionContextSelector.saveToConfiguration(m_configuration);
        // persist model state
        m_configuration.saveSettings(settings);
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
     * @param inSpecs port 0 is an optional space connector. If nothing is connected, local workflows can be executed.
     *            If something is connected, the upstream must deliver a space connector that is fit for instantiating
     *            an {@link IServerConnection}. port 1 must provide a DataTableSpec. It is kept to find JSON input
     *            columns in {@link #onWorkflowPathChanged(String)}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs)
        throws NotConfigurableException {

        m_controls.m_workflowChooser.loadSettingsFrom(settings, inSpecs);
        m_configuration.loadSettingsInDialog(settings);

        m_controls.m_connectionControls.loadConfiguration(m_configuration);

        m_inputTableSpec = (DataTableSpec)inSpecs[CallWorkflowRowBased3NodeFactory.getDataPortIndex(m_configuration)];

        // load settings
        m_controls.m_createReport.loadFromConfiguration(m_configuration);

        // disable timeouts and backoff controls if connection is local
        m_controls.m_connectionControls.setRemoteConnection(m_configuration.getWorkflowChooserModel().getLocation());
        try {
            m_controls.m_executionContextSelector.loadSettingsInDialog(m_configuration);

        } catch (InvalidSettingsException e) {
            throw new NotConfigurableException(e.getMessage(), e);
        }

        fetchCalleeWorkflowParameters();
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
    }

    static class ParameterUpdateWorker extends SwingWorkerWithContext<Map<String, ExternalNodeData>, Void> {

        private final CallWorkflowConnectionConfiguration m_configuration;

        private final Consumer<Map<String, ExternalNodeData>> m_comboBoxAdjuster;

        private final Consumer<String> m_errorDisplay;

        ParameterUpdateWorker(final CallWorkflowConnectionConfiguration configuration,
            final Consumer<Map<String, ExternalNodeData>> parameterUiAdjuster, final Consumer<String> errorDisplay) {
            m_configuration = configuration;
            m_comboBoxAdjuster = parameterUiAdjuster;
            m_errorDisplay = errorDisplay;
        }

        /**
         * the map is not empty {@inheritDoc}
         */
        @Override
        protected Map<String, ExternalNodeData> doInBackgroundWithContext() throws Exception {
            if (StringUtils.isEmpty(m_configuration.getWorkflowPath())) {
                return Map.of();
            }
            ConnectionUtil.validateConfiguration(m_configuration);
            try (var backend = ConnectionUtil.createWorkflowBackend(m_configuration)) {
                return backend.getInputNodes();
            }
        }

        @Override
        protected void doneWithContext() {
            if (!isCancelled()) {
                try {
                    var excecutionContextMap = get();
                    if (excecutionContextMap != null) {
                        m_comboBoxAdjuster.accept(excecutionContextMap);
                    }
                } catch (InterruptedException | CancellationException e) {
                    NodeLogger.getLogger(getClass()).warn(e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    m_errorDisplay.accept(ExceptionUtils.getRootCauseMessage(e));
                }
            }
        }
    }

}
