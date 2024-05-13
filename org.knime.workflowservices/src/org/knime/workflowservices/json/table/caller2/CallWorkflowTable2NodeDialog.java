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
package org.knime.workflowservices.json.table.caller2;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.json.container.credentials.ContainerCredentialsJsonSchema;
import org.knime.core.data.json.container.table.ContainerTableJsonSchema;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.filehandling.core.util.GBCBuilder;
import org.knime.workflowservices.CalleeParameterFlow;
import org.knime.workflowservices.CalleePropertyFlow;
import org.knime.workflowservices.Fetcher;
import org.knime.workflowservices.HubCalleeSelectionFlow;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.InvocationTargetPanel;
import org.knime.workflowservices.InvocationTargetProvider;
import org.knime.workflowservices.InvocationTargetProviderWorkflowChooserImplementation;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration.ConnectionType;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.connection.util.CallWorkflowConnectionControls;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.json.table.caller.CallWorkflowTableNodeConfiguration;
import org.knime.workflowservices.json.table.caller.ParameterId;

import jakarta.json.JsonValue;

/**
 * Dialog for Call Workflow (Table Based) node as of 4.7.0
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class CallWorkflowTable2NodeDialog extends NodeDialogPane
    implements Fetcher.StatefulConsumer<List<Map<String, JsonValue>>> {

    /** Manages asynchronous data fetching. */
    private final CalleePropertyFlow m_calleePropertyFlow;

    private static final NodeLogger LOGGER = NodeLogger.getLogger(CallWorkflowTable2NodeDialog.class);

    private final CallWorkflowConnectionControls m_serverSettings = new CallWorkflowConnectionControls();

    private final InvocationTargetPanel m_invocationTargetPanel;

    /** For errors when fetching workflow parameters */
    private final JLabel m_workflowErrorLabel = new JLabel();

    private ParameterSelection m_inputParameterSelectionPanel;

    private ParameterSelection m_outputParameterSelectionPanel;

    private ParameterSelection m_flowVariableDestination;

    private ParameterSelection m_flowCredentialsDestination;

    private JCheckBox m_useFullyQualifiedNamesChecker;

    private String m_configuredInputParameter;

    private String m_configuredOutputParameter;

    private String m_configuredFlowVariableDestination;

    private String m_configuredFlowCredentialsDestination;

    private boolean m_hasInputTable;

    private final CallWorkflowTableNodeConfiguration m_configuration;

    private boolean m_loading;

    CallWorkflowTable2NodeDialog(final CallWorkflowTableNodeConfiguration config) {

        m_configuration = config;
        m_invocationTargetPanel = new InvocationTargetPanel(m_configuration, this);

        final var versionSelector = m_invocationTargetPanel.getVersionSelector();
        final var parameterDisplay = this;
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

        var gbc = new GridBagLayout();
        final var p = new JPanel(gbc);
        final var padding = 10;
        p.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        p.add(m_serverSettings.getMainPanel(), gbc);
        p.add(m_invocationTargetPanel.createExecutionPanel(), gbc);

        p.add(createParameterPanel(), gbc);

        addTab("Workflow", new JScrollPane(p));
        addTab("Advanced Settings", createAdvancedTab());
    }

    /*
     * Callbacks
     */

    private List<Map<String, JsonValue>> fetchParameters(final CallWorkflowConnectionConfiguration configuration)
        throws Exception {
        final var isEmptyCallee = switch (configuration.getConnectionType()) {
            case FILE_SYSTEM -> StringUtils.isEmpty(configuration.getWorkflowPath());
            case HUB_AUTHENTICATION -> StringUtils.isEmpty(configuration.getDeploymentId());
        };
        if(isEmptyCallee) {
            return List.of();
        }

        var tempConfig = configuration.createFetchConfiguration();
        m_serverSettings.saveToConfiguration(tempConfig);
        ConnectionUtil.validateConfiguration(tempConfig);

        try (var backend = ConnectionUtil.createWorkflowBackend(tempConfig)) {
            if (backend != null) {
                return Arrays.asList(getInputNodeValues(backend), backend.getOutputValuesForConfiguration());
            } else {
                return null;
            }
        }
    }
    private static Map<String, JsonValue> getInputNodeValues(final IWorkflowBackend backend) {
        Map<String, JsonValue> inputNodes = new HashMap<>();
        for (Map.Entry<String, ExternalNodeData> e : backend.getInputNodes().entrySet()) {
            var json = e.getValue().getJSONValue();
            if (json != null) {
                inputNodes.put(e.getKey(), json);
            }
        }
        return inputNodes;
    }


    @Override
    public void clear() {
        m_loading = false;
        m_workflowErrorLabel.setText("");
    }

    @Override
    public void loading() {
        m_loading = true;
        m_workflowErrorLabel.setText("");
    }

    @Override
    public void accept(final List<Map<String, JsonValue>> parameterMaps) {
        m_loading = false;

        var inputNodeData = parameterMaps.get(0);

        // Given a map of input node data, each entry corresponding to a container input node in the
        //   called workflow, we want to partition these entries by type of container input node
        //   s.t. we can constrain the range of choices to only those of matching type
        //  (e.g. as a target container input node for table data the user should only be able to
        //   select Container Input (Table) nodes).
        // There is currently no clean way to determine the type of Container Input node based
        //   on the given input node data. As a heuristic, this is determined based on the
        //   example/template JSON that some container input nodes provide. Table and Credential
        //   inputs provide characteristic template JSON. Flow Variable input nodes, however, can
        //   accept any JSON containing key/value pairs (since AP-16680).
        // Partition the given map based on matching the provided template JSONs against each node's
        //   schema. Provide table and credential inputs with only those choices that can be matched,
        //   all other cases are eligible to be pushed as flow variables.
        // See AP-17403.
        Map<String, JsonValue> tableInputs = new HashMap<>();
        Map<String, JsonValue> credentialInputs = new HashMap<>();
        Map<String, JsonValue> otherInputs = new HashMap<>();
        for (Entry<String, JsonValue> entry : inputNodeData.entrySet()) {
            if (ContainerTableJsonSchema.hasContainerTableJsonSchema(entry.getValue())) {
                tableInputs.put(entry.getKey(), entry.getValue());
            } else if (ContainerCredentialsJsonSchema.hasValidSchema(entry.getValue())) {
                credentialInputs.put(entry.getKey(), entry.getValue());
            } else {
                otherInputs.put(entry.getKey(), entry.getValue());
            }
        }

        m_inputParameterSelectionPanel.update(tableInputs, m_configuredInputParameter);
        if (!m_hasInputTable) {
            m_inputParameterSelectionPanel.setEnabled(false);
            if (!m_inputParameterSelectionPanel.isEmpty()) {
                m_inputParameterSelectionPanel.setWarning("No input table connected");
            }
        }
        m_flowCredentialsDestination.update(credentialInputs, m_configuredFlowCredentialsDestination);
        m_flowVariableDestination.update(otherInputs, m_configuredFlowVariableDestination);

        Map<String, JsonValue> outputNodeData = parameterMaps.get(1).entrySet().stream()
            .filter(entry -> ContainerTableJsonSchema.hasContainerTableJsonSchema(entry.getValue()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        m_outputParameterSelectionPanel.update(outputNodeData, m_configuredOutputParameter);

        m_workflowErrorLabel.setText("");
    }

    @Override
    public void exception(final String cause) {
        m_loading = false;

        m_inputParameterSelectionPanel.clearParameters();
        m_outputParameterSelectionPanel.clearParameters();
        m_flowVariableDestination.clearParameters();
        LOGGER.info(cause);

        // TODO

//        Pair<String, Throwable> errorPair;
//        if (cause instanceof InvalidSettingsException) {
//            // addresses AP-19370: bogus error message when wrong node is in Callee
//            errorPair = Pair.of(cause + " (wrong node types in use?)", cause);
//        } else {
//            errorPair = Pair.of(ExceptionUtils.getRootCauseMessage(e), ExceptionUtils.getRootCause(e));
//        }
//
//        if (!(cause instanceof InvalidSettingsException)) {
//            LOGGER.debug(errorPair.getLeft(), errorPair.getRight());
//        }
//        m_workflowErrorLabel.setText(errorPair.getLeft());

    }

    /*
     * UI
     */

    private JPanel createParameterPanel() {
        var parameterPanel = new JPanel(new GridBagLayout());
        parameterPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        final var gbc = new GBCBuilder().resetX().resetY().anchorLineStart().fillHorizontal();

        m_inputParameterSelectionPanel =
            new ParameterSelection(new JLabel("Assign input table to:  "), new JComboBox<>(), new JLabel());
        parameterPanel.add(m_inputParameterSelectionPanel, gbc.incY().setWeightX(1).insetLeft(5).build());

        m_outputParameterSelectionPanel =
            new ParameterSelection(new JLabel("Fill output table from:  "), new JComboBox<>(), new JLabel());
        parameterPanel.add(m_outputParameterSelectionPanel, gbc.incY().setWeightX(1).insetLeft(5).build());

        m_flowVariableDestination =
            new ParameterSelection(new JLabel("Push flow variables to:"), new JComboBox<>(), new JLabel());
        parameterPanel.add(m_flowVariableDestination, gbc.incY().setWeightX(1).insetLeft(5).build());

        m_flowCredentialsDestination =
            new ParameterSelection(new JLabel("Push flow credentials to:"), new JComboBox<>(), new JLabel());
        parameterPanel.add(m_flowCredentialsDestination, gbc.incY().setWeightX(1).insetLeft(5).build());

        m_useFullyQualifiedNamesChecker = new JCheckBox("Use Fully Qualified Name for Input and Output Parameters");
        m_useFullyQualifiedNamesChecker.addItemListener(e -> m_calleePropertyFlow.invocationTargetUpdated());
        m_useFullyQualifiedNamesChecker.addItemListener(e -> m_calleePropertyFlow.invocationTargetUpdated());
        m_useFullyQualifiedNamesChecker.addItemListener(e -> m_calleePropertyFlow.invocationTargetUpdated());

        parameterPanel.add(m_useFullyQualifiedNamesChecker, gbc.incY().setWeightX(1).insetLeft(5).build());

        return parameterPanel;
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(!m_loading, "Can't apply configuration while analysis is ongoing");
        storeUiStateInConfiguration();
        m_invocationTargetPanel.saveSettingsTo(settings, m_configuration);
        m_configuration.save(settings);
    }

    /**
     * Apply the state of the user interface to {@link #m_configuration}.
     *
     * @throws InvalidSettingsException
     */
    private void storeUiStateInConfiguration() throws InvalidSettingsException {
        CheckUtils.checkSetting(!m_loading , "Can't apply configuration while analysis is ongoing");

        m_serverSettings.saveToConfiguration(m_configuration);

        var useFullyqualifiedName = m_useFullyQualifiedNamesChecker.isSelected();

        var selectedInput = m_inputParameterSelectionPanel.getSelectedParameter();
        String selectedInputId = null;
        if (selectedInput != null) {
            selectedInputId = selectedInput.getId(useFullyqualifiedName);
        }
        m_configuration.setSelectedInputParameter(selectedInputId);

        var selectedOutput = m_outputParameterSelectionPanel.getSelectedParameter();
        String selectedOutputId = null;
        if (selectedOutput != null) {
            selectedOutputId = selectedOutput.getId(useFullyqualifiedName);
        }
        m_configuration.setSelectedOutputParameter(selectedOutputId);

        var selectedFlowVariableDestination = m_flowVariableDestination.getSelectedParameter();
        String selectedFlowVariableDestinationId = null;
        if (selectedFlowVariableDestination != null) {
            selectedFlowVariableDestinationId = selectedFlowVariableDestination.getId(useFullyqualifiedName);
        }
        m_configuration.setFlowVariableDestination(selectedFlowVariableDestinationId);

        var selectedFlowCredentialsDestination = m_flowCredentialsDestination.getSelectedParameter();
        String selectedFlowCredentialsDestinationId = null;
        if (selectedFlowCredentialsDestination != null) {
            selectedFlowCredentialsDestinationId = selectedFlowCredentialsDestination.getId(useFullyqualifiedName);
        }
        m_configuration.setFlowCredentialsDestination(selectedFlowCredentialsDestinationId);

        m_configuration.setUseQualifiedId(useFullyqualifiedName);

        m_configuration.setBackoffPolicy(m_serverSettings.getBackoffPanel().getSelectedBackoffPolicy());
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        // when re-opening the dialog, delay data fetching until in consistent state again.
        m_calleePropertyFlow.enable(false);
        try {
            m_invocationTargetPanel.loadChooser(settings, specs);
            m_configuration.loadInDialog(settings);
            m_invocationTargetPanel.loadSettingsInDialog(m_configuration, settings, specs);
            loadConfiguration(m_configuration, specs);

            final var versionSelectorVisible =
                ConnectionUtil.isHubConnection(m_calleePropertyFlow.getInvocationTarget().getFileSystemType());
            m_invocationTargetPanel.getVersionSelector().setVisible(versionSelectorVisible);
        } finally {
            m_calleePropertyFlow.enable(true);
        }

        // fetch remote data
        m_calleePropertyFlow.loadInvocationTargets();
        loadConfiguration(m_configuration, specs);
    }

    /**
     * Synchronize the user interface with the current configuration
     *
     * @throws InvalidSettingsException
     * @throws NotConfigurableException
     */
    private void loadConfiguration(final CallWorkflowTableNodeConfiguration configuration,
        final PortObjectSpec[] inSpecs) throws NotConfigurableException {

        m_serverSettings.loadConfiguration(configuration);

        m_useFullyQualifiedNamesChecker.setSelected(configuration.isUseFullyQualifiedId());

        var tableSpec = inSpecs[CallWorkflowTable2NodeFactory.getDataPortIndex(configuration)];

        if (tableSpec == null) {
            m_inputParameterSelectionPanel.setEnabled(false);
            m_hasInputTable = false;
        } else {
            m_inputParameterSelectionPanel.setEnabled(true);
            m_hasInputTable = true;
        }

        // If we open the dialog a second time and an panelUpdater is currently running (probably waiting
        // for the workflow lock because the workflow to call is already executing) we need to cancel it to avoid
        // filling the panelMap twice

        m_inputParameterSelectionPanel.clearParameters();
        m_outputParameterSelectionPanel.clearParameters();
        m_flowVariableDestination.clearParameters();
        m_flowCredentialsDestination.clearParameters();

        // Have to be set after setText() is called on m_selectWorkflowPath!
        m_configuredInputParameter = configuration.getSelectedInputParameter();
        m_configuredOutputParameter = configuration.getSelectedOutputParameter();
        m_configuredFlowVariableDestination = configuration.getFlowVariableDestination();
        m_configuredFlowCredentialsDestination = configuration.getFlowCredentialsDestination();


        m_serverSettings.getBackoffPanel()
            .setSelectedBackoffPolicy(configuration.getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY));
        if (m_configuration.getConnectionType() == ConnectionType.HUB_AUTHENTICATION) {
            m_serverSettings.setRemoteConnection();
        } else {
            m_serverSettings.setRemoteConnection(m_configuration.getWorkflowChooserModel().getLocation());
        }
    }

    /**
     * When a new workflow has been selected the old configuration is not longer relevant and should be removed.
     */
    private void removeConfiguredSelections() {
        m_configuredInputParameter = null;
        m_configuredOutputParameter = null;
        m_configuredFlowVariableDestination = null;
        m_configuredFlowCredentialsDestination = null;
    }

    @Override
    public void onClose() {
        m_calleePropertyFlow.close();
        m_invocationTargetPanel.close();
        super.onClose();
    }

    private JPanel createAdvancedTab() {
        final var container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(m_serverSettings.getTimeoutControls().getPanel());
        container.add(m_serverSettings.getBackoffPanel());
        container.add(Box.createHorizontalGlue());
        return container;
    }

    /**
     * Extension of a swing worker that fetches both the input and output values from a workflow backend and then
     * populates the selectable input and output parameter combo boxes of this dialog with the values, maintaining
     * previous selection if present and still valid.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */

    /**
     * Panel holding an info label, a combo box where a parameter selection can be made and a warning label.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ParameterSelection extends JPanel {

        private static final long serialVersionUID = -2153316791050239330L;

        private final JLabel m_infoLabel;

        private final JComboBox<ParameterId> m_parameterSelection;

        private final JLabel m_warningLabel;

        private Map<String, String> m_fullyQualifiedToSimple = new HashMap<>();

        private Map<String, String> m_simpleToFullyQualified = new HashMap<>();

        ParameterSelection(final JLabel infoLabel, final JComboBox<ParameterId> parameterSelection,
            final JLabel warningLabel) {
            super(new GridBagLayout());
            m_infoLabel = infoLabel;
            m_parameterSelection = parameterSelection;
            m_parameterSelection.setRenderer(new ParameterRenderer());
            m_warningLabel = warningLabel;
            m_warningLabel.setForeground(Color.RED.darker());
            m_parameterSelection.addActionListener(l -> m_warningLabel.setText(""));
            m_parameterSelection.setEnabled(false);
            createLayout();
        }

        void setWarning(final String warning) {
            m_warningLabel.setText(warning);
        }

        private void createLayout() {
            var gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.gridx = gbc.gridy = 0;
            gbc.weightx = 0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(5, 50, 5, 5);

            add(m_infoLabel, gbc);

            gbc.gridx++;
            add(m_parameterSelection, gbc);

            gbc.gridx = 1;
            gbc.gridy++;
            add(m_warningLabel, gbc);
        }

        boolean isEmpty() {
            return m_parameterSelection.getItemCount() == 0;
        }

        @Override
        public void setEnabled(final boolean enabled) {
            if (m_parameterSelection.getItemCount() == 0) {
                m_parameterSelection.setEnabled(false);
            } else {
                m_parameterSelection.setEnabled(enabled);
            }
        }

        @Override
        public boolean isEnabled() {
            return m_parameterSelection.isEnabled();
        }

        /**
         * Update this input component with choices based on {@code parameterToNodeData}.
         *
         * @param parameterToNodeData map from Container Input node identifier to its JSON template, each entry
         *            corresponding to a container input node in the callee that is deemed compatible with this
         *            particular ParameterSelection instance.
         * @param configuredParameter
         */
        void update(final Map<String, JsonValue> parameterToNodeData, final String configuredParameter) {
            var alreadySelected = (ParameterId)m_parameterSelection.getSelectedItem();
            resetAndLoad(parameterToNodeData);
            setSelection(alreadySelected, configuredParameter);
        }

        private void resetAndLoad(final Map<String, JsonValue> nodeData) {
            clearSelectionsAndIdMaps();
            var serviceParameters = getServiceParameters(nodeData);

            if (serviceParameters.isEmpty()) {
                m_parameterSelection.setEnabled(false);
            } else {
                m_parameterSelection.setEnabled(true);
                serviceParameters.sort(ParameterId::compareTo);
                serviceParameters.forEach(m_parameterSelection::addItem);
            }
        }

        private void clearSelectionsAndIdMaps() {
            m_fullyQualifiedToSimple.clear();
            m_simpleToFullyQualified.clear();
            m_parameterSelection.removeAllItems();
            m_parameterSelection.setSelectedIndex(-1);
        }

        private List<ParameterId> getServiceParameters(final Map<String, JsonValue> nodeData) {
            List<ParameterId> serviceParameters = new ArrayList<>();
            var fullyQualifiedToSimpleIDMap =
                IWorkflowBackend.getFullyQualifiedToSimpleIDMap(nodeData.keySet());
            for (Entry<String, JsonValue> parameterToDataEntry : nodeData.entrySet()) {
                var fullyQualifiedId = parameterToDataEntry.getKey();
                var simpleId = fullyQualifiedToSimpleIDMap.get(fullyQualifiedId);
                m_fullyQualifiedToSimple.put(fullyQualifiedId, simpleId);
                m_simpleToFullyQualified.put(simpleId, fullyQualifiedId);
                serviceParameters.add(new ParameterId(fullyQualifiedId, simpleId));
            }
            return serviceParameters;
        }

        private void setSelection(final ParameterId selectedItem, final String configuredSelection) {
            if (shouldLoadFromConfiguration(selectedItem, configuredSelection)) {
                setSelectionBasedOnConfiguration(configuredSelection);
            } else {
                m_warningLabel.setText(" ");
                m_parameterSelection.setSelectedItem(selectedItem);
                if (m_parameterSelection.getSelectedIndex() == -1 && m_parameterSelection.getItemCount() > 0) {
                    m_parameterSelection.setSelectedIndex(0);
                }
            }
        }

        /**
         * The first time the dialog is opened the selection should be fetched from the configuration.
         */
        private boolean shouldLoadFromConfiguration(final ParameterId selectedItem, final String configuredSelection) {
            return selectedItem == null && StringUtils.isNotEmpty(configuredSelection);
        }

        private void setSelectionBasedOnConfiguration(final String configuredSelection) {
            ParameterId selectedParameterId = null;
            if (m_configuration.isUseFullyQualifiedId()) {
                if (m_fullyQualifiedToSimple.containsKey(configuredSelection)) {
                    selectedParameterId =
                        new ParameterId(configuredSelection, m_fullyQualifiedToSimple.get(configuredSelection));
                }
            } else {
                if (m_simpleToFullyQualified.containsKey(configuredSelection)) {
                    selectedParameterId =
                        new ParameterId(m_simpleToFullyQualified.get(configuredSelection), configuredSelection);
                }
            }
            m_parameterSelection.setSelectedItem(selectedParameterId);
            if (m_parameterSelection.getSelectedIndex() == -1) {
                m_warningLabel.setText("Configured parameter \"" + configuredSelection
                    + "\" does not longer exist in the selected workflow.");
            } else {
                m_warningLabel.setText(" ");
            }
        }

        void clearParameters() {
            m_parameterSelection.removeAllItems();
            m_parameterSelection.setSelectedIndex(-1);
            if (m_parameterSelection.getItemCount() == 0) {
                m_parameterSelection.setEnabled(false);
            }
        }

        ParameterId getSelectedParameter() {
            return (ParameterId)m_parameterSelection.getSelectedItem();
        }
    }

    /**
     * Custom renderer that decides whether to display the fully qualified id or the simple id of a parameter in the
     * combo-box, based on the users selection.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ParameterRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = -5747012371561384934L;

        @Override
        public Component getListCellRendererComponent(@SuppressWarnings("rawtypes") final JList list,
            final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {

            String text = null;
            if (value instanceof ParameterId parameter) {
                var useFullyQualifiedName = m_useFullyQualifiedNamesChecker.isSelected();
                text = parameter.getId(useFullyQualifiedName);
            }

            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}
