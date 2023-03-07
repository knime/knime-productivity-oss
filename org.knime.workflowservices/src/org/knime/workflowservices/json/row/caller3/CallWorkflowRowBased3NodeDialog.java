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
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.workflowservices.Deployment;
import org.knime.workflowservices.InvocationTargetPanel;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.connection.util.CreateReportControls;

/**
 * Allows selecting a source column for each callee workflow input parameter, configuration of reporting, etc.
 *
 * Establishes a connection during {@link #loadSettingsFrom(NodeSettingsRO, PortObjectSpec[])} to list
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

    private final CallWorkflowConnectionControls m_connectionControls;

    private ParameterUpdateWorker m_parameterUpdateWorker;

    private final JsonInputParametersPanel m_inputParameters;

    private final CreateReportControls m_createReport;

    private final InvocationTargetPanel m_invocationTargetPanel;

    CallWorkflowRowBased3NodeDialog(final CallWorkflowRowBased3Configuration callWorkflowRowBasedConfiguration) {
        m_configuration = callWorkflowRowBasedConfiguration;
        m_connectionControls = new CallWorkflowConnectionControls();
        m_inputParameters = new JsonInputParametersPanel();
        m_invocationTargetPanel = new InvocationTargetPanel(callWorkflowRowBasedConfiguration, this);
        // m_serverSettings needs to be created already
        m_createReport = new CreateReportControls(runReport -> {
            if (runReport.booleanValue()) {
                m_connectionControls.forcePolling();
            } else {
                m_connectionControls.enablePollingSelection();
            }
        });

        addTab("Workflow", createMainTab());
        addTab("Advanced Settings", createAdvancedTab());

        // Callback wiring
        m_invocationTargetPanel.addDeploymentChangedListener(this::deploymentChanged);
        m_configuration.getWorkflowChooserModel().addChangeListener(e -> fetchWorkflowProperties());
    }

    /*
     * Callbacks
     */

    private void deploymentChanged(final Deployment newDeployment) {
        m_configuration.setDeploymentId(newDeployment.id());
        fetchWorkflowProperties();
    }

    /**
     * Retrieve the callee workflow input parameters and pass them to the {@link Controls#m_inputParameters} panel to
     * allow binding data to the input parameters.
     *
     * @throws InvalidSettingsException
     */
    private void fetchWorkflowProperties() {
        if (m_parameterUpdateWorker != null) {
            m_parameterUpdateWorker.cancel(true);
            m_parameterUpdateWorker = null;
        }
        var tempConfig = m_configuration.createFetchConfiguration();

        m_parameterUpdateWorker =
            new ParameterUpdateWorker(tempConfig, this::onWorkflowParametersLoad, this::onFailure);
        m_parameterUpdateWorker.execute();
    }

    private void onWorkflowParametersLoad(final Map<String, ExternalNodeData> inputNodes) {
        m_inputParameters.createPanels(inputNodes, m_inputTableSpec);
        m_inputParameters.loadConfiguration(m_configuration);
        m_inputParameters.clearError();

        var panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();
    }

    private void onFailure(final String message) {
        m_inputParameters.setError(message);
        NodeLogger.getLogger(CallWorkflowRowBased3NodeDialog.class).debug(message);
    }

    /*
     * Load/save
     */

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        // apply GUI state to model state
        m_connectionControls.saveToConfiguration(m_configuration);
        m_inputParameters.saveToConfiguration(m_configuration);
        m_createReport.saveToConfiguration(m_configuration);

        // persist model state
        m_invocationTargetPanel.saveSettingsTo(settings, m_configuration);
        m_configuration.saveSettings(settings);
    }

    /**
     * Load configuration and establish a server connection using the optional space connector at port 0. If no space
     * connector is present, create a local connection.
     *
     * Fetch the input and output parameters of the configured callee workflow.
     *
     * {@inheritDoc}
     *
     * @param inSpecs port 0 is an optional connector. If nothing is connected, local workflows can be executed. If
     *            something is connected, the upstream must deliver a space connector that is fit for instantiating port
     *            1 must provide a DataTableSpec. It is kept to find JSON input columns in
     *            {@link #onWorkflowPathChanged(String)}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs)
        throws NotConfigurableException {

        m_invocationTargetPanel.loadChooser(settings, inSpecs);
        m_configuration.loadSettingsInDialog(settings);
        m_invocationTargetPanel.loadSettingsInDialog(m_configuration, settings, inSpecs);

        m_connectionControls.loadConfiguration(m_configuration);

        m_inputTableSpec = (DataTableSpec)inSpecs[CallWorkflowRowBased3NodeFactory.getDataPortIndex(m_configuration)];

        // load settings
        m_createReport.loadFromConfiguration(m_configuration);

        // disable timeouts and backoff controls if connection is local
        if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
            m_connectionControls.setRemoteConnection();
        } else {
            m_connectionControls.setRemoteConnection(m_configuration.getWorkflowChooserModel().getLocation());
        }
        fetchWorkflowProperties();
    }


    /**
     * @return contains the main server connection settings (polling, job discard/keep), workflow path selector, input
     *         parameter mapping and report controls.
     */
    public JPanel createMainTab() {
        final var p = new JPanel();
        final var padding = 10;
        p.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        p.add(m_connectionControls.getMainPanel());
        p.add(m_invocationTargetPanel.createExecutionPanel());
        p.add(m_inputParameters.getPanel());
        p.add(m_createReport.getPanel());

        return p;
    }

    private JPanel createAdvancedTab() {
        final var container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(m_connectionControls.getTimeoutControls().getPanel());
        container.add(m_connectionControls.getBackoffPanel());
        container.add(Box.createHorizontalGlue());
        return container;
    }

    @Override
    public void onClose() {
        if (m_parameterUpdateWorker != null) {
            m_parameterUpdateWorker.cancel(true);
            m_parameterUpdateWorker = null;
        }
        m_invocationTargetPanel.close();
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
            if (isEmptyCallee()) {
                return Map.of();
            }
            ConnectionUtil.validateConfiguration(m_configuration);
            try (var backend = ConnectionUtil.createWorkflowBackend(m_configuration)) {
                return backend.getInputNodes();
            }
        }

        private boolean isEmptyCallee() {
            if (m_configuration.getConnectionType() == ConnectionType.FILE_SYSTEM) {
                return StringUtils.isEmpty(m_configuration.getWorkflowPath());
            } else {
                return StringUtils.isEmpty(m_configuration.getDeploymentId());
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
