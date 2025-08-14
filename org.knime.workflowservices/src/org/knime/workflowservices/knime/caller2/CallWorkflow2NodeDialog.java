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

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.ConfigurableNodeFactory.ConfigurableNodeDialog;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
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
import org.knime.workflowservices.CalleeParameterFlow;
import org.knime.workflowservices.CalleePropertyFlow;
import org.knime.workflowservices.HubCalleeSelectionFlow;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.InvocationTargetPanel;
import org.knime.workflowservices.InvocationTargetProvider;
import org.knime.workflowservices.InvocationTargetProviderWorkflowChooserImplementation;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.knime.caller.CallWorkflowNodeConfiguration;
import org.knime.workflowservices.knime.caller.PanelWorkflowParameters;
import org.knime.workflowservices.knime.caller.ParameterUpdateWorker;
import org.knime.workflowservices.knime.caller.WorkflowParameter;
import org.knime.workflowservices.knime.caller.WorkflowParameters;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
final class CallWorkflow2NodeDialog extends NodeDialogPane implements ConfigurableNodeDialog {

    /*
     * State
     */

    private final CallWorkflowNodeConfiguration m_configuration;

    /**
     * Set to true after the node's ports have been adapted to a callee workflow's in- and output ports.
     *
     * @see #updateNodePorts(NodeCreationConfiguration)
     */
    private volatile boolean m_portConfigChanged;

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
     * Async data fetching
     */

    private final CalleePropertyFlow m_calleePropertyFlow;

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

        final var versionSelector = m_invocationTargetPanel.getVersionSelector();
        final var parameterDisplay = m_parameterMappingPanel;
        InvocationTargetProvider<?> invocationTarget;
        if (m_configuration.getConnectionType() == ConnectionType.FILE_SYSTEM) {
            invocationTarget =
                new InvocationTargetProviderWorkflowChooserImplementation(m_configuration.getWorkflowChooserModel());
            m_calleePropertyFlow = new HubCalleeSelectionFlow<>(m_configuration, invocationTarget, versionSelector,
                parameterDisplay, this::fetchParameters);
        } else {
            invocationTarget = m_invocationTargetPanel.getDeploymentSelector();
            m_calleePropertyFlow =
                new CalleeParameterFlow<>(m_configuration, invocationTarget, parameterDisplay, this::fetchParameters);
        }

        addTab("Workflow", getMainPanel());
        addTab("Advanced Settings", createAdvancedTab());
    }

    /**
     * Synchronous parameter fetch operation that is wrapped in a swing worker via {@link CalleeParameterFlow}.
     * @param configuration to create the connection,
     * @return input and output parameters of the callee workflow
     * @throws Exception if the backend cannot be created or the input/output resource descriptions cannot be retrieved
     */
    private WorkflowParameters fetchParameters(final CallWorkflowConnectionConfiguration configuration)
        throws Exception {
        final var isEmptyCallee = switch (configuration.getConnectionType()) {
            case FILE_SYSTEM -> StringUtils.isEmpty(configuration.getWorkflowPath());
            case HUB_AUTHENTICATION -> StringUtils.isEmpty(configuration.getDeploymentId());
        };
        if (isEmptyCallee) {
            // need to check here, otherwise we would attempt to create a workflow backend from
            // no selected workflow (would fail with "No such node ID..." in core), same checks in:
            // - `org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3NodeDialog#fetchParameters`
            // - `org.knime.workflowservices.json.table.caller2.CallWorkflowTable2NodeDialog#fetchParameters`
            return null;
        }
        m_configuration.setCalleeWorkflowProperties(null);
        try (IWorkflowBackend backend = ConnectionUtil.createWorkflowBackend(configuration)) {
            var parameters =
                new WorkflowParameters(backend.getInputResourceDescription(), backend.getOutputResourceDescription());
            m_configuration.setCalleeWorkflowProperties(parameters);
            return parameters;
        }
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
     * @throws InvalidSettingsException if no workflow parameters are stored in the node configuration
     */
    private void updateNodePorts() throws InvalidSettingsException {

        // should never happen - setCurrentNodeCreationConfiguration would be broken
        final var portConfig = m_nodeCreationConfig.getPortConfig().orElseThrow();

        // should not happen - the configuration should not be able to finish without configuring a callee workflow and
        // fetching its workflow parameters
        var properties = m_configuration.getCalleeWorkflowProperties().orElseThrow(
            () -> new InvalidSettingsException("Could not find the parameters of the workflow to be called."));

        // update input ports
        ExtendablePortGroup inputConfig =
            (ExtendablePortGroup)portConfig.getGroup(CallWorkflow2NodeFactory.INPUT_PORT_GROUP);
        while (inputConfig.hasConfiguredPorts()) {
            inputConfig.removeLastPort();
        }
        for (WorkflowParameter portDesc : properties.getInputParameters()) {
            inputConfig.addPort(portDesc.getPortType());
        }

        // update output ports
        ExtendablePortGroup outputConfig =
            (ExtendablePortGroup)portConfig.getGroup(CallWorkflow2NodeFactory.OUTPUT_PORT_GROUP);
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
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
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

        m_invocationTargetPanel.saveSettingsTo(settings, m_configuration);
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
        }
        m_configuration.saveSettings(settings);
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] inSpecs)
        throws NotConfigurableException {

        // when re-opening the dialog, delay data fetching until in consistent state again.
        m_calleePropertyFlow.enable(false);
        try {
            // callee location also updates the selection flow location via listener
            m_invocationTargetPanel.loadChooser(settings, inSpecs);

            // call workflow connection base settings & workflow parameters
            m_configuration.loadSettingsInDialog(settings);

            // Hub authenticator connected: deployment & authenticator
            // File system connected: load version and execution context
            m_invocationTargetPanel.loadSettingsInDialog(m_configuration, settings, inSpecs);

            // call workflow connection settings
            m_connectionControls.loadConfiguration(m_configuration);

            // configure remote execution if a server connection is present
            enableAllUIElements(true);
            if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
                m_connectionControls.setRemoteConnection();
            } else {
                m_connectionControls.setRemoteConnection(m_configuration.getWorkflowChooserModel().getLocation());
            }

            // use load instead of `StatefulConsumer#accept` to initialize before consuming properties,
            // avoids NPE if `PanelWorkflowParameters#get` is null
            m_configuration.getCalleeWorkflowProperties().ifPresent(m_parameterMappingPanel::load);

            final var versionSelectorVisible =
                ConnectionUtil.isHubConnection(m_calleePropertyFlow.getInvocationTarget().getFileSystemType());
            m_invocationTargetPanel.getVersionSelector().setVisible(versionSelectorVisible);
        } finally {
            m_calleePropertyFlow.enable(true);
        }

        // fetch remote data
        m_calleePropertyFlow.loadInvocationTargets();
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
        m_calleePropertyFlow.close();
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
        final var newNodeCreationConfiguration = Optional.ofNullable(m_portConfigChanged ? m_nodeCreationConfig : null);
        m_portConfigChanged = false;
        return newNodeCreationConfiguration;
    }
}
