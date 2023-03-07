package org.knime.workflowservices.knime.caller2;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

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
import org.knime.workflowservices.Deployment;
import org.knime.workflowservices.InvocationTargetPanel;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.knime.caller.CallWorkflowNodeConfiguration;
import org.knime.workflowservices.knime.caller.PanelWorkflowParameters;
import org.knime.workflowservices.knime.caller.ParameterUpdateWorker;
import org.knime.workflowservices.knime.caller.WorkflowParameter;
import org.knime.workflowservices.knime.caller.WorkflowParameters;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
class CallWorkflow2NodeDialog extends NodeDialogPane implements ConfigurableNodeDialog {

    /*
     * State
     */

    private final CallWorkflowNodeConfiguration m_configuration;

    /**
     * Set to true after the node's ports have been adapted to a callee workflow's in- and output ports.
     *
     * @see #updateNodePorts(NodeCreationConfiguration)
     */
    private boolean m_portConfigChanged = false;

    /**
     * Needs to be {@link ModifiableNodeCreationConfiguration} instead of a regular {@link NodeCreationConfiguration}
     * since the dialog can be used to configure a callee and that callee's workflow parameters will be used to
     * configure the node's ports.
     *
     * Provides information about the callee workflow input/output parameters (since during configuration node ports are
     * synced to them, see {@link #setCurrentNodeCreationConfiguration(ModifiableNodeCreationConfiguration)}).
     */
    private ModifiableNodeCreationConfiguration m_nodeCreationConfig;

    /*
     * Asynchronous workers
     */

    /** Asynchronously fetches the workflow input and output parameters of a workflow to be executed. */
    private ParameterUpdateWorker m_parameterUpdater;

    /*
     * Controls
     */

    /** Map input ports to workflow parameters. */
    private final PanelWorkflowParameters m_parameterMappingPanel;

    /** Which workflow or deployment to call. */
    private final InvocationTargetPanel m_invocationTargetPanel;

    /** How to call the workflow or deployment. */
    private final CallWorkflowConnectionControls m_connectionControls = new CallWorkflowConnectionControls();


    CallWorkflow2NodeDialog(final NodeCreationConfiguration ncc) {
        m_configuration =
            new CallWorkflowNodeConfiguration(ncc, CallWorkflow2NodeFactory.CONNECTION_INPUT_PORT_GRP_NAME);
        m_nodeCreationConfig = (ModifiableNodeCreationConfiguration)ncc;

        m_invocationTargetPanel = new InvocationTargetPanel(m_configuration, this);

        m_parameterMappingPanel = new PanelWorkflowParameters(this::parametersCompatibleWithPorts, m_configuration,
            ncc.getPortConfig().orElseThrow(IllegalStateException::new));

        addTab("Workflow", getMainPanel());
        addTab("Advanced Settings", createAdvancedTab());

        // callbacks wiring
        m_configuration.getWorkflowChooserModel().addChangeListener(e -> fetchWorkflowProperties());
        m_invocationTargetPanel.addDeploymentChangedListener(this::deploymentChanged);
    }

    /*
     * Callbacks
     */

    private void deploymentChanged(final Deployment newDeployment) {
        m_configuration.setDeploymentId(newDeployment.id());
        fetchWorkflowProperties();
    }

    /**
     * Asynchronously fetch input and output parameters of the workflow to be executed.
     * {@link #onWorkflowPropertiesLoad(WorkflowParameters)} is the callback for the result.
     */
    private void fetchWorkflowProperties() {

        if (m_configuration.getConnectionType() == ConnectionType.FILE_SYSTEM
            && !m_configuration.getWorkflowChooserModel().isLocationValid()) {
            NodeLogger.getLogger(getClass())
                .warn("Invalid location: " + m_configuration.getWorkflowChooserModel().getPath());
            return;
        }

        var inProgress =
            !(m_parameterUpdater == null || m_parameterUpdater.isDone() || m_parameterUpdater.cancel(true));
        if (inProgress) {
            m_parameterMappingPanel.setErrorMessage("Failed to interrupt fetching workflow parameters.");
            return;
        }

        m_parameterMappingPanel.setState(PanelWorkflowParameters.State.LOADING);

        var fetchConfig = m_configuration.createFetchConfiguration();
        m_parameterUpdater = new ParameterUpdateWorker(fetchConfig,
            m_parameterMappingPanel::setErrorMessage, this::onWorkflowPropertiesLoad);

        m_configuration.setCalleeWorkflowProperties(null);
        m_parameterUpdater.execute();
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

    /*
     * Port adaption logic
     */

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
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
        while (inputConfig.hasConfiguredPorts()) {
            inputConfig.removeLastPort();
        }
        for (WorkflowParameter portDesc : properties.getInputParameters()) {
            inputConfig.addPort(portDesc.getPortType());
        }

        // update output ports
        ExtendablePortGroup outputConfig =
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
        while (outputConfig.hasConfiguredPorts()) {
            outputConfig.removeLastPort();
        }
        for (WorkflowParameter portDesc : properties.getOutputParameters()) {
            outputConfig.addPort(portDesc.getPortType());
        }

        m_portConfigChanged = true;
    }

    /**
     * Checks whether the given workflow input and output parameters are compatible with the node's port configuration.
     * Compatible means that the input parameter types (in the order defined in the {@link WorkflowParameters} match the
     * input ports of a node, and the output parameters match the output ports. If compatible, no node connections will
     * be removed when calling {@link #updateNodePorts()}.
     *
     * NB: When swapping two ports with the same type, the routing of the data will change, but existing connections at
     * those ports will not be removed, thus, the new {@link WorkflowParameters} will still be considered compatible to
     * the node's ports.
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
        List<PortType> inputDataPortTypes = new LinkedList<>(Arrays.asList(inputPorts));
        // if a connector is present, it is not a data port
        m_configuration.getConnectorPortIndex().ifPresent(i -> inputDataPortTypes.remove(i.intValue()));

        PortType[] outputParameterTypes =
            properties.getOutputParameters().stream().map(WorkflowParameter::getPortType).toArray(PortType[]::new);
        // all output ports are data ports
        PortType[] outputPortTypes = portConfig.getOutputPorts();

        return Arrays.equals(inputParameterTypes, inputDataPortTypes.toArray(PortType[]::new))
            && Arrays.equals(outputParameterTypes, outputPortTypes);
    }

    /*
     * Load / save
     */

    @Override
    protected final void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.ERROR,
            "Cannot apply configuration when workflow parameters could not be successfully fetched.");
        CheckUtils.checkSetting(m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.LOADING,
            "Cannot apply configuration while fetching workflow parameters.");
        CheckUtils.checkSetting(
            m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.PARAMETER_CONFLICT,
            "Please confirm adjusting the ports of the node to match the workflow parameters.");
        CheckUtils.checkSetting(
            m_parameterMappingPanel.getState() != PanelWorkflowParameters.State.NO_WORKFLOW_SELECTED,
            "Please select a workflow to execute.");

        m_connectionControls.saveToConfiguration(m_configuration);

        // update the order of the parameters
        if (m_configuration.getCalleeWorkflowProperties().isPresent()) {
            var cwp = m_configuration.getCalleeWorkflowProperties().get(); // NOSONAR
            cwp.setInputParameterOrder(m_parameterMappingPanel.getInputParameterOrder());
            cwp.setOutputParameterOrder(m_parameterMappingPanel.getOutputParameterOrder());
        }

        // create input/output ports according to the selected order of the workflow input/output parameters
        if (!parametersCompatibleWithPorts(m_configuration.getCalleeWorkflowProperties().orElseThrow())) {
            updateNodePorts();
        } else {
            m_portConfigChanged = false;
        }

        m_invocationTargetPanel.saveSettingsTo(settings, m_configuration);
        m_configuration.saveSettings(settings);
    }

    @Override
    protected final void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs)
        throws NotConfigurableException {
        m_invocationTargetPanel.loadChooser(settings, inSpecs);
        m_configuration.loadSettingsInDialog(settings);
        m_invocationTargetPanel.loadSettingsInDialog(m_configuration, settings, inSpecs);
        m_connectionControls.loadConfiguration(m_configuration);

        // configure remote execution if a server connection is present
        enableAllUIElements(true);
        if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
            m_connectionControls.setRemoteConnection();
        } else {
            m_connectionControls.setRemoteConnection(m_configuration.getWorkflowChooserModel().getLocation());
        }

        // display workflow input/output parameters
        fetchWorkflowProperties();
    }

    private void enableAllUIElements(final boolean enable) {
        m_connectionControls.enableAllUIElements(enable);
        m_configuration.getWorkflowChooserModel().setEnabled(enable);
    }

    Component getMainPanel() {
        final var mainPanel = new JPanel(new GridBagLayout());
        final var padding = 10;
        mainPanel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(m_connectionControls.getMainPanel());
        mainPanel.add(m_invocationTargetPanel.createExecutionPanel());
        mainPanel.add(m_parameterMappingPanel.getContentPane());
        return new JScrollPane(mainPanel);
    }

    /**
     * @return panel with timeouts and backoff policy controls
     */
    JPanel createAdvancedTab() {
        final var container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(m_connectionControls.getTimeoutControls().getPanel());
        container.add(m_connectionControls.getBackoffPanel());
        container.add(Box.createHorizontalGlue());
        return container;
    }

    @Override
    public void onClose() {
        if (m_parameterUpdater != null) {
            m_parameterUpdater.cancel(true);
            m_parameterUpdater = null;
        }
        m_invocationTargetPanel.close();
        super.onClose();
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
        return Optional.ofNullable(m_portConfigChanged ? m_nodeCreationConfig : null);
    }

}
