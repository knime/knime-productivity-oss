package org.knime.workflowservices.knime.caller;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.ConfigurableNodeFactory.ConfigurableNodeDialog;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.context.ModifiableNodeCreationConfiguration;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.ExtendablePortGroup;
import org.knime.core.node.context.ports.ModifiablePortsConfiguration;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
class CallWorkflowNodeDialog extends NodeDialogPane implements ConfigurableNodeDialog {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(CallWorkflowNodeDialog.class);

    /*
     * State
     */

    private IServerConnection m_serverConnection;

    private CallWorkflowNodeConfiguration m_configuration = new CallWorkflowNodeConfiguration();

    private ModifiableNodeCreationConfiguration m_nodeCreationConfig;

    /*
     * GUI elements
     */

    private final PanelServerSettings m_serverSettingsPanel;

    /**
     * Displays which workflow input/output parameter is assigned to which input/output port. Uses the given method to
     * check whether parameter configuration (type and order of input and output parameters) is compatible to the node's
     * port configuration.
     */
    private final PanelWorkflowParameters m_parameterMappingPanel;

    /**
     * Shows locally or remotely available workflow paths. A path can be selected and will be copied over to
     * {@link #m_selectWorkflowPath}.
     */
    private final DialogSelectWorkflow m_selectWorkflowDialog =
        new DialogSelectWorkflow(getParentFrame(), this::setSelectedWorkflow, this::fetchRemoteWorkflows);

    /*
     * Asynchronous workers
     */

    /** Asynchronously fetches the workflow input and output parameters of a workflow to be executed. */
    private ParameterUpdateWorker m_parameterUpdater;

    /** Asynchronously fetches available workflows from a KNIME Server. */
    private final AtomicReference<ListWorkflowsWorker> m_listWorkflowsWorker = new AtomicReference<>(null);

    CallWorkflowNodeDialog(final NodeCreationConfiguration creationConfig) {

        m_nodeCreationConfig = (ModifiableNodeCreationConfiguration)creationConfig;
        m_parameterMappingPanel = new PanelWorkflowParameters(this::parametersCompatibleWithPorts,
            m_nodeCreationConfig.getPortConfig().orElseThrow(() -> new IllegalStateException("Coding error.")));

        final var mainPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;

        m_serverSettingsPanel = new PanelServerSettings(gbc);
        gbc.gridwidth = 3;
        mainPanel.add(m_serverSettingsPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;

        // text field for workflow path input and browse workflows button
        createSelectWorkflowControls(mainPanel, gbc);

        // configure timeout on loading a workflow
        createLoadTimeOutControls(mainPanel, gbc);

        m_fetchWorkflowPropertiesButton.addActionListener(l -> fetchWorkflowProperties());
        gbc.gridx = 2;
        mainPanel.add(m_fetchWorkflowPropertiesButton, gbc);

        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.weighty = 1;
        gbc.weightx = 1;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;

        mainPanel.add(m_parameterMappingPanel.getContentPane(), gbc);

        addTab("Workflow to Execute", new JScrollPane(mainPanel));

    }

    /**
     * @param panel
     * @param gbc
     */
    private void createLoadTimeOutControls(final JPanel panel, final GridBagConstraints gbc) {
        ((JSpinner.DefaultEditor)m_loadTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        var timeoutLabel = new JLabel("Fetch timeout [s]: ");
        panel.add(timeoutLabel, gbc);
        gbc.gridx++;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(m_loadTimeoutSpinner, gbc);
        var timeoutTooltip = "The maximum number of seconds to wait when fetching the parameters of workflow";
        timeoutLabel.setToolTipText(timeoutTooltip);
        m_loadTimeoutSpinner.setToolTipText(timeoutTooltip);
    }

    /**
     * @param panel
     * @param gbc
     */
    private void createSelectWorkflowControls(final JPanel panel, final GridBagConstraints gbc) {
        m_selectWorkflowPath.setEditable(true);

        m_selectWorkflowPath.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(final DocumentEvent e) {
                // not nice: when setting the text of the input,
                // it will fire a remove update (setting to "" first) and then an insert update
                // causing an error to be shown (Empty string is an invalid path) for a tiny moment
                fetchWorkflowProperties();
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                fetchWorkflowProperties();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                fetchWorkflowProperties();
            }
        });

        m_selectWorkflowPath.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    setSelectedWorkflow(m_selectWorkflowPath.getText());
                }
            }
        });
        var workflowLabel = new JLabel("Workflow Path: ");
        panel.add(workflowLabel, gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        m_selectWorkflowPath.setPreferredSize(new Dimension(500, 20));
        m_selectWorkflowPath.setMaximumSize(new Dimension(1000, 20));
        panel.add(m_selectWorkflowPath, gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.NONE;
        m_selectWorkflowButton.addActionListener(l -> m_selectWorkflowDialog.open());
        panel.add(m_selectWorkflowButton, gbc);
    }

    /**
     * Called when the {@link ParameterUpdateWorker} is done with retrieving information about the callee workflow.
     *
     * @param remoteWorkflowProperties retrieved information, e.g., input ports and output ports.
     */
    private void onWorkflowPropertiesLoad(final WorkflowParameters remoteWorkflowProperties) {
        // store result
        m_configuration.setCalleeWorkflowProperties(remoteWorkflowProperties);
        // show result
        m_parameterMappingPanel.update(remoteWorkflowProperties);

        // mark retrieval as done
        m_parameterUpdater = null;
    }

    /**
     * Configures the input and output ports of the node such that they reflect the input/output parameters of the
     * callee workflow in the order selected in the dialog.
     *
     * Called when finishing configuration in {@link #saveSettingsTo(NodeSettingsWO)}.
     *
     * @param properties describes the input and output ports of the callee workflow.
     * @throws InvalidSettingsException
     */
    private void updateNodePorts() throws InvalidSettingsException {

        Optional<ModifiablePortsConfiguration> portConfig = m_nodeCreationConfig.getPortConfig();
        if (portConfig.isEmpty()) {
            return; // should never happen - setCurrentNodeCreationConfiguration would be broken
        }

        // should not happen - the configuration should not be able to finish without configuring a callee workflow and
        // fetching its workflow parameters
        var properties = m_configuration.getCalleeWorkflowProperties().orElseThrow(
            () -> new InvalidSettingsException("Could not find the parameters of the workflow to be called."));

        // update input ports
        ExtendablePortGroup inputConfig =
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflowNodeFactory.INPUT_PORT_GROUP);
        while (inputConfig.hasConfiguredPorts()) {
            inputConfig.removeLastPort();
        }
        for (WorkflowParameter portDesc : properties.getInputParameters()) {
            inputConfig.addPort(portDesc.getPortType());
        }

        // update output ports
        ExtendablePortGroup outputConfig =
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflowNodeFactory.OUTPUT_PORT_GROUP);
        while (outputConfig.hasConfiguredPorts()) {
            outputConfig.removeLastPort();
        }
        for (WorkflowParameter portDesc : properties.getOutputParameters()) {
            outputConfig.addPort(portDesc.getPortType());
        }

        m_portConfigChanged = true;
    }

    @Override
    protected final void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.ERROR,
            "Cannot apply configuration when workflow parameters could not be successfully fetched.");
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.LOADING,
            "Cannot apply configuration while fetching workflow parameters.");
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.PARAMETER_CONFLICT,
            "Please confirm adjusting the ports of the node to match the workflow parameters.");
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.NO_WORKFLOW_SELECTED,
            "Please select a workflow to execute.");

        m_serverSettingsPanel.saveConfiguration(m_configuration);
        m_configuration.setWorkflowPath(m_selectWorkflowPath.getText());
        m_configuration.setLoadTimeout(Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue()));

        // update the order of the parameters
        if (m_configuration.getCalleeWorkflowProperties().isPresent()) {
            var cwp = m_configuration.getCalleeWorkflowProperties().get(); // NOSONAR
            cwp.setInputParameterOrder(m_parameterMappingPanel.getInputParameterOrder());
            cwp.setOutputParameterOrder(m_parameterMappingPanel.getOutputParameterOrder());
        }

        // create input/output ports according to the selected order of the workflow input/output parameters
        updateNodePorts();

        m_configuration.saveSettings(settings);
    }

    @Override
    protected final void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        try {
            m_configuration = new CallWorkflowNodeConfiguration();
            m_configuration.loadSettingsInDialog(settings);
        } catch (InvalidSettingsException e) {
            throw new NotConfigurableException("Could not load settings in dialog.", e);
        }

        Duration loadTimeout = m_configuration.getLoadTimeout().orElse(IServerConnection.DEFAULT_LOAD_TIMEOUT);
        m_loadTimeoutSpinner.setValue(loadTimeout.getSeconds());

        m_serverSettingsPanel.loadConfiguration(m_configuration);

        var wfm = NodeContext.getContext().getWorkflowManager();
        if (wfm.getContext().isTemporaryCopy()) {
            m_serverConnection = null;
            disableAllUIElements();
            m_parameterMappingPanel
                .setErrorMessage("This node cannot be configured in a temporary copy of the workflow.");
            return;
        } else {
            // configure remote execution if a server connection is present
            final var spec = (FileSystemPortObjectSpec)specs[0];
            enableAllUIElements();
            try {
                m_serverConnection = ServerConnectionUtil.getConnection(spec, wfm);
                m_serverSettingsPanel.m_serverAddress.setText(m_serverConnection.getHost());
            } catch (InvalidSettingsException e) {
                getLogger().debug(e.getMessage(), e);
                disableAllUIElements();
                m_parameterMappingPanel.setErrorMessage(e.getMessage());
                m_serverConnection = null;
            }
        }

        // display workflow input/output parameters
        m_configuration.getCalleeWorkflowProperties().ifPresent(m_parameterMappingPanel::load);
        m_selectWorkflowPath.setText(m_configuration.getWorkflowPath());
    }

    /**
     * TODO everything below is copied from {@link AbstractCallWorkflowTableNodeDialogPane}
     */

    /** Calls {@link #fetchWorkflowProperties()} on every change. */
    private final JTextField m_selectWorkflowPath = new JTextField();

    private final JButton m_selectWorkflowButton = new JButton("Browse workflows");

    private final JButton m_fetchWorkflowPropertiesButton = new JButton("Fetch workflow parameters");

    private final JSpinner m_loadTimeoutSpinner =
        new JSpinner(new SpinnerNumberModel((int)IServerConnection.DEFAULT_LOAD_TIMEOUT.getSeconds(), 0, null, 30));

    /**
     * Set to true after the node's ports have been adapted to a callee workflow's in- and output ports.
     *
     * @see #updateNodePorts(NodeCreationConfiguration)
     */
    private boolean m_portConfigChanged = false;

    private Frame getParentFrame() {
        Frame frame = null;
        var container = getPanel().getParent();
        while (container != null) {
            if (container instanceof Frame) {
                frame = (Frame)container;
                break;
            }
            container = container.getParent();
        }
        return frame;
    }

    /**
     * Get a list of workflow paths from the connected KNIME Server. This may take a while and is done asynchronously.
     * The worker will call {@link DialogSelectWorkflow#setWorkflowPaths(List)} on {@link #m_selectWorkflowDialog}.
     */
    private void fetchRemoteWorkflows() {
        if (m_listWorkflowsWorker.get() == null) {
            var worker = new ListWorkflowsWorker();
            m_listWorkflowsWorker.set(worker);
            worker.execute();
        }
    }

    private void disableAllUIElements() {
        m_selectWorkflowButton.setEnabled(false);
        m_fetchWorkflowPropertiesButton.setEnabled(false);
        m_selectWorkflowPath.setEnabled(false);
        m_loadTimeoutSpinner.setEnabled(false);

        m_serverSettingsPanel.m_asyncInvocationChecker.setEnabled(false);
        m_serverSettingsPanel.m_syncInvocationChecker.setEnabled(false);
        m_serverSettingsPanel.m_retainJobOnFailure.setEnabled(false);
        m_serverSettingsPanel.m_discardJobOnSuccesfulExecution.setEnabled(false);
    }

    private void enableAllUIElements() {
        m_selectWorkflowButton.setEnabled(true);
        m_fetchWorkflowPropertiesButton.setEnabled(true);
        m_selectWorkflowPath.setEnabled(true);
        m_loadTimeoutSpinner.setEnabled(true);

        m_serverSettingsPanel.m_asyncInvocationChecker.setEnabled(true);
        m_serverSettingsPanel.m_syncInvocationChecker.setEnabled(true);
        m_serverSettingsPanel.m_retainJobOnFailure.setEnabled(true);
        m_serverSettingsPanel.m_discardJobOnSuccesfulExecution.setEnabled(true);
    }

    private void setSelectedWorkflow(final String selectedWorkflow) {
        m_selectWorkflowPath.setText(selectedWorkflow); // triggers updater
    }

    /**
     * Asynchronously fetch input and output parameters of the workflow to be executed.
     * {@link #onWorkflowPropertiesLoad(WorkflowParameters)} is the callback for the result.
     */
    private void fetchWorkflowProperties() {
        var inProgress =
            !(m_parameterUpdater == null || m_parameterUpdater.isDone() || m_parameterUpdater.cancel(true));
        if (inProgress) {
            m_parameterMappingPanel.setErrorMessage("Failed to interrupt fetching workflow parameters.");
            return;
        }

        m_parameterMappingPanel.setState(PanelWorkflowParameters.State.LOADING);

        var timeOut = Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue());

        try {
            m_parameterUpdater = new ParameterUpdateWorker(//
                m_selectWorkflowPath.getText(), //
                m_parameterMappingPanel::setErrorMessage, //
                timeOut, //
                m_serverConnection, //
                this::onWorkflowPropertiesLoad,//
                CallWorkflowNodeConfiguration::new);

            m_configuration.setCalleeWorkflowProperties(null);
            m_parameterUpdater.execute();
        } catch(Exception e){
            m_parameterMappingPanel.setErrorMessage(e.getMessage());
        }
    }

    /**
     * Checks whether the given workflow input and output parameters are compatible with the node's port configuration.
     * Compatible means that the input parameter types (in the order defined in the {@link WorkflowParameters}
     * match the input ports of a node, and the output parameters match the output ports. If compatible, no node
     * connections will be removed when calling {@link #updateNodePorts()}.
     *
     * NB: When swapping two ports with the same type, the routing of the data will change, but existing connections at
     * those ports will not be removed, thus, the new {@link WorkflowParameters} will still be considered
     * compatible to the node's ports.
     *
     * @param properties workflow input and output parameters
     * @return true if {@link WorkflowParameters} are compatible with the node's port configuration
     */
    private boolean parametersCompatibleWithPorts(final WorkflowParameters properties) {
        ModifiablePortsConfiguration portConfig = m_nodeCreationConfig.getPortConfig()
            .orElseThrow(() -> new IllegalStateException("Coding error. No port configuration found."));

        PortType[] inputParameterTypes =
            properties.getInputParameters().stream().map(WorkflowParameter::getPortType).toArray(PortType[]::new);
        PortType[] inputPorts = portConfig.getInputPorts();
        // skip first (optional file system) port
        PortType[] inputPortTypes = Arrays.copyOfRange(inputPorts, 1, inputPorts.length);

        PortType[] outputParameterTypes =
            properties.getOutputParameters().stream().map(WorkflowParameter::getPortType).toArray(PortType[]::new);
        PortType[] outputPortTypes = portConfig.getOutputPorts();

        return Arrays.equals(inputParameterTypes, inputPortTypes)
            && Arrays.equals(outputParameterTypes, outputPortTypes);

    }

    @Override
    public void onClose() {
        if (m_parameterUpdater != null) {
            m_parameterUpdater.cancel(true);
            m_parameterUpdater = null;
        }

        if (m_listWorkflowsWorker.get() != null) {
            m_listWorkflowsWorker.get().cancel(true);
            m_listWorkflowsWorker.set(null);
        }

        super.onClose();
    }

    /**
     * Extension of a swing worker that fetches all workflows available from a remote server;
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ListWorkflowsWorker extends SwingWorkerWithContext<List<String>, Void> {

        @Override
        protected List<String> doInBackgroundWithContext() throws Exception {
            List<String> listedWorkflows;
            try {
                listedWorkflows = m_serverConnection.listWorkflows();
            } catch (ListWorkflowFailedException e) {
                Throwable rootCause = ExceptionUtils.getRootCause(e);
                throw rootCause instanceof Exception ? (Exception)rootCause : e;
            }
            Collections.sort(listedWorkflows, String.CASE_INSENSITIVE_ORDER);
            return listedWorkflows;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void doneWithContext() {
            try {
                List<String> listedWorkflows = get();
                m_selectWorkflowDialog.setWorkflowPaths(listedWorkflows);
                m_listWorkflowsWorker.set(null);
            } catch (InterruptedException | CancellationException ex) {
                m_selectWorkflowDialog.clearWorkflowList();
                // do nothing
            } catch (Exception ex) {
                m_selectWorkflowDialog.clearWorkflowList();
                var pair = ServerConnectionUtil.handle(ex);
                m_selectWorkflowDialog.setErrorText(pair.getLeft());
                LOGGER.error(pair.getLeft(), pair.getRight());
            }
        }

    }

    /**
     * To provide up-to-date information about the port configuration of the node before opening the dialog.
     * {@inheritDoc}
     */
    @Override
    public void setCurrentNodeCreationConfiguration(final ModifiableNodeCreationConfiguration nodeCreationConfig) {
        m_nodeCreationConfig = nodeCreationConfig;
    }

    @Override
    public Optional<ModifiableNodeCreationConfiguration> getNewNodeCreationConfiguration() {
        var newConfig = Optional.ofNullable(m_portConfigChanged ? m_nodeCreationConfig : null);
        return newConfig;
    }

}
