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
package org.knime.workflowservices.json.table.caller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import javax.json.JsonValue;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.knime.core.node.util.FilterableListModel;
import org.knime.core.node.util.VerticalCollapsablePanels;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;
import org.knime.workflowservices.connection.LocalExecutionServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Shared dialog components for Call Workflow nodes.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
public abstract class AbstractCallWorkflowTableNodeDialogPane extends NodeDialogPane {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(AbstractCallWorkflowTableNodeDialogPane.class);

    private final JLabel m_serverAddress;

    private IServerConnection m_serverConnection;

    private final JRadioButton m_syncInvocationChecker;

    private final JRadioButton m_asyncInvocationChecker;

    private final JCheckBox m_retainJobOnFailure;

    private final JCheckBox m_discardJobOnSuccesfulExecution;

    private final JTextField m_selectWorkflowPath;

    private final JSpinner m_loadTimeoutSpinner;

    private final JButton m_selectWorkflowButton;

    private final JLabel m_workflowErrorLabel;

    private final JLabel m_stateErrorLabel;

    private ParameterUpdater m_parameterUpdater;

    private final VerticalCollapsablePanels m_advancedSettingsCollapsible;

    private final ParameterSelection m_inputParameterSelectionPanel;

    private final ParameterSelection m_outputParameterSelectionPanel;

    private final ParameterSelection m_flowVariableDestination;

    private final ParameterSelection m_flowCredentialsDestination;

    private final JCheckBox m_useFullyQualifiedNamesChecker;

    private final JProgressBar m_loadingAnimation;

    private final JLabel m_loadingMessage;

    private boolean m_validWorkflowPath;

    private String m_configuredInputParameter;

    private String m_configuredOutputParameter;

    private String m_configuredFlowVariableDestination;

    private String m_configuredFlowCredentialsDestination;

    private JList<String> m_workflowList;

    private SwingWorkerWithContext<List<String>, Void> m_workflowLister;

    private boolean m_hasInputTable;

    private FilterableListModel m_filteredWorkflowModel = new FilterableListModel(Collections.emptyList());

    private CallWorkflowTableNodeConfiguration m_configuration = new CallWorkflowTableNodeConfiguration();

    AbstractCallWorkflowTableNodeDialogPane() {
        final JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_workflowList = new JList<>(m_filteredWorkflowModel);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;

        JPanel serverSettingsPanel = new JPanel(new GridBagLayout());
        serverSettingsPanel.setBorder(BorderFactory.createTitledBorder("KNIME Server Call Settings"));
        serverSettingsPanel.add(new JLabel("Server address:"), gbc);

        gbc.gridx++;
        m_serverAddress = new JLabel("No server connected");

        serverSettingsPanel.add(m_serverAddress, gbc);

        m_syncInvocationChecker = new JRadioButton
            ("Short duration: the workflow is expected to run quickly (less than 10 seconds)");
        m_asyncInvocationChecker = new JRadioButton(
            "Long duration: the workflow is expected to run longer than 10 seconds");
        JPanel invocationPanel = createInvocationPanel();
        invocationPanel.setBorder(BorderFactory.createTitledBorder("Invocation"));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        serverSettingsPanel.add(invocationPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        m_retainJobOnFailure = new JCheckBox("Retain job on failure");
        serverSettingsPanel.add(m_retainJobOnFailure, gbc);

        gbc.gridy++;
        m_discardJobOnSuccesfulExecution = new JCheckBox("Discard job on successful execution");
        serverSettingsPanel.add(m_discardJobOnSuccesfulExecution, gbc);

        gbc.gridwidth = 3;
        panel.add(serverSettingsPanel, gbc);


        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        m_selectWorkflowPath = new JTextField();
        m_selectWorkflowPath.setEditable(true);

        m_selectWorkflowPath.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(final DocumentEvent e) {
                update();
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                update();
            }

            void update() {
                removeConfiguredSelections();
                updateParameters();
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
        JLabel workflowLabel = new JLabel("Workflow Path: ");
        panel.add(workflowLabel, gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        m_selectWorkflowPath.setPreferredSize(new Dimension(500, 20));
        m_selectWorkflowPath.setMaximumSize(new Dimension(1000, 20));
        panel.add(m_selectWorkflowPath, gbc);

        gbc.gridx++;
        gbc.fill = GridBagConstraints.NONE;
        m_selectWorkflowButton = new JButton("Browse workflows");
        m_selectWorkflowButton.addActionListener(l -> openSelectWorkflowDialog());
        panel.add(m_selectWorkflowButton, gbc);

        m_loadTimeoutSpinner = new JSpinner(new SpinnerNumberModel((int)IServerConnection.DEFAULT_LOAD_TIMEOUT.getSeconds(), 0, null, 30));
        ((JSpinner.DefaultEditor)m_loadTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        JLabel timeoutLabel = new JLabel("Load timeout [s]: ");
        panel.add(timeoutLabel, gbc);
        gbc.gridx++;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(m_loadTimeoutSpinner, gbc);
        String timeoutTooltip = "The timeout to use when loading a remote workflow";
        timeoutLabel.setToolTipText(timeoutTooltip);
        m_loadTimeoutSpinner.setToolTipText(timeoutTooltip);

        gbc.gridx = 1;
        gbc.gridy++;
        gbc.gridwidth = 2;
        m_workflowErrorLabel = new JLabel();
        m_workflowErrorLabel.setForeground(Color.RED.darker());
        panel.add(m_workflowErrorLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy++;
        gbc.gridwidth = 2;
        m_stateErrorLabel = new JLabel();
        m_stateErrorLabel.setForeground(Color.RED.darker());
        panel.add(m_stateErrorLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        JPanel loadingBox = new JPanel();
        loadingBox.setLayout(new BoxLayout(loadingBox, BoxLayout.PAGE_AXIS));

        m_loadingMessage = new JLabel();
        m_loadingMessage.setForeground(Color.RED.darker());
        m_loadingMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
        m_loadingAnimation = new JProgressBar();
        m_loadingAnimation.setVisible(false);
        m_loadingAnimation.setIndeterminate(true);
        m_loadingAnimation.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingBox.add(m_loadingAnimation);
        loadingBox.add(m_loadingMessage);

        gbc.gridx++;
        panel.add(loadingBox, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        m_advancedSettingsCollapsible = new VerticalCollapsablePanels();
        m_advancedSettingsCollapsible.setLayout(new GridBagLayout());
        panel.add(m_advancedSettingsCollapsible, gbc);

        gbc.gridwidth = 4;
        gbc.weighty = 1;
        gbc.weightx = 1;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;
        Box fillerBox = Box.createHorizontalBox();
        panel.add(fillerBox, gbc);

        final JPanel parameterSelectionPanel = new JPanel(new GridBagLayout());
        parameterSelectionPanel.setName("Advanced settings");
        GridBagConstraints gbcAdvancedSettings = new GridBagConstraints();
        gbcAdvancedSettings.anchor = GridBagConstraints.LINE_START;
        gbcAdvancedSettings.gridx = gbcAdvancedSettings.gridy = 0;
        gbcAdvancedSettings.weightx = 0;
        gbcAdvancedSettings.weighty = 0;
        gbcAdvancedSettings.gridwidth = 2;
        gbcAdvancedSettings.fill = GridBagConstraints.NONE;
        gbcAdvancedSettings.insets = new Insets(5, 50, 5, 5);

        m_inputParameterSelectionPanel =
            new ParameterSelection(
                new JLabel("Assign input table to:  "),
                new JComboBox<ParameterId>(),
                new JLabel());
        parameterSelectionPanel.add(m_inputParameterSelectionPanel, gbcAdvancedSettings);

        gbcAdvancedSettings.gridx = 0;
        gbcAdvancedSettings.gridy++;

        m_outputParameterSelectionPanel =
            new ParameterSelection(
                new JLabel("Fill output table from:  "),
                new JComboBox<ParameterId>(),
                new JLabel());
        parameterSelectionPanel.add(m_outputParameterSelectionPanel, gbcAdvancedSettings);

        gbcAdvancedSettings.gridx = 0;
        gbcAdvancedSettings.gridy++;
        m_flowVariableDestination =
                new ParameterSelection(
                    new JLabel("Push flow variables to:"),
                    new JComboBox<ParameterId>(),
                    new JLabel());
        parameterSelectionPanel.add(m_flowVariableDestination, gbcAdvancedSettings);

        gbcAdvancedSettings.gridx = 0;
        gbcAdvancedSettings.gridy++;
        m_flowCredentialsDestination =
                new ParameterSelection(
                    new JLabel("Push flow credentials to:"),
                    new JComboBox<ParameterId>(),
                    new JLabel());

        parameterSelectionPanel.add(m_flowCredentialsDestination, gbcAdvancedSettings);

        gbcAdvancedSettings.gridx = 0;
        gbcAdvancedSettings.gridy++;
        gbcAdvancedSettings.gridwidth = 3;
        gbcAdvancedSettings.weightx=1;
        m_useFullyQualifiedNamesChecker = new JCheckBox("Use Fully Qualified Name for Input and Output Parameters");
        m_useFullyQualifiedNamesChecker.addItemListener(e -> updateParameters());
        m_useFullyQualifiedNamesChecker.addItemListener(e -> updateParameters());
        m_useFullyQualifiedNamesChecker.addItemListener(e -> updateParameters());
        parameterSelectionPanel.add(m_useFullyQualifiedNamesChecker, gbcAdvancedSettings);

        m_advancedSettingsCollapsible.addPanel(parameterSelectionPanel, true);

        addTab("Workflow", new JScrollPane(panel));
    }

    private JPanel createInvocationPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        ButtonGroup bg = new ButtonGroup();
        bg.add(m_syncInvocationChecker);
        bg.add(m_asyncInvocationChecker);
        m_syncInvocationChecker.doClick();

        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        p.add(m_syncInvocationChecker, gbc);
        gbc.gridy += 1;
        p.add(m_asyncInvocationChecker, gbc);

        return p;
    }

    private void openSelectWorkflowDialog() {
        SelectWorkflowDialog selectWorkflowDialog = new SelectWorkflowDialog(getParentFrame());
        selectWorkflowDialog.setVisible(true);
        selectWorkflowDialog.dispose();
    }

    private Frame getParentFrame() {
        Frame frame = null;
        Container container = getPanel().getParent();
        while (container != null) {
            if (container instanceof Frame) {
                frame = (Frame)container;
                break;
            }
            container = container.getParent();
        }
        return frame;
    }

    @Override
    protected final void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_validWorkflowPath, "Invalid workflow path");
        CheckUtils.checkSetting(m_parameterUpdater == null, "Can't apply configuration while analysis is ongoing");
        m_configuration = new CallWorkflowTableNodeConfiguration();
        saveConfiguration(m_configuration);
        m_configuration.save(settings);
    }

    void saveConfiguration(final CallWorkflowTableNodeConfiguration configuration) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_validWorkflowPath, "Invalid workflow path");
        CheckUtils.checkSetting(m_parameterUpdater == null, "Can't apply configuration while analysis is ongoing");

        configuration.setSynchronousInvocation(m_syncInvocationChecker.isSelected());
        configuration.setKeepFailingJobs(m_retainJobOnFailure.isSelected());
        configuration.setDiscardJobOnSuccessfulExecution(m_discardJobOnSuccesfulExecution.isSelected());

        configuration.setWorkflowPath(m_selectWorkflowPath.getText());
        configuration.setLoadTimeout(Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue()));

        boolean useFullyqualifiedName = m_useFullyQualifiedNamesChecker.isSelected();

        ParameterId selectedInput = m_inputParameterSelectionPanel.getSelectedParameter();
        String selectedInputId = null;
        if (selectedInput != null) {
            selectedInputId = selectedInput.getId(useFullyqualifiedName);
        }
        configuration.setSelectedInputParameter(selectedInputId);

        ParameterId selectedOutput = m_outputParameterSelectionPanel.getSelectedParameter();
        String selectedOutputId = null;
        if (selectedOutput != null) {
            selectedOutputId = selectedOutput.getId(useFullyqualifiedName);
        }
        configuration.setSelectedOutputParameter(selectedOutputId);

        ParameterId selectedFlowVariableDestination = m_flowVariableDestination.getSelectedParameter();
        String selectedFlowVariableDestinationId = null;
        if (selectedFlowVariableDestination != null) {
            selectedFlowVariableDestinationId = selectedFlowVariableDestination.getId(useFullyqualifiedName);
        }
        configuration.setFlowVariableDestination(selectedFlowVariableDestinationId);

        ParameterId selectedFlowCredentialsDestination = m_flowCredentialsDestination.getSelectedParameter();
        String selectedFlowCredentialsDestinationId = null;
        if (selectedFlowCredentialsDestination != null) {
            selectedFlowCredentialsDestinationId = selectedFlowCredentialsDestination.getId(useFullyqualifiedName);
        }
        configuration.setFlowCredentialsDestination(selectedFlowCredentialsDestinationId);

        configuration.setUseQualifiedId(useFullyqualifiedName);

    }

    @Override
    protected final void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
            throws NotConfigurableException {
        try {
            m_configuration = new CallWorkflowTableNodeConfiguration().loadInDialog(settings);
        } catch (InvalidSettingsException e) {
            throw new NotConfigurableException("Could not load settings in dialog", e);
        }
        loadConfiguration(m_configuration, specs);
    }

    void loadConfiguration(final CallWorkflowTableNodeConfiguration configuration, final PortObjectSpec[] specs) {
        Duration loadTimeout = configuration.getLoadTimeout().orElse(IServerConnection.DEFAULT_LOAD_TIMEOUT);
        m_loadTimeoutSpinner.setValue(loadTimeout.getSeconds());

        if (configuration.isSynchronousInvocation()) {
            m_syncInvocationChecker.doClick();
        } else {
            m_asyncInvocationChecker.doClick();
        }

        m_retainJobOnFailure.setSelected(configuration.isKeepFailingJobs());
        m_discardJobOnSuccesfulExecution.setSelected(configuration.isDiscardJobOnSuccessfulExecution());
        m_useFullyQualifiedNamesChecker.setSelected(configuration.isUseFullyQualifiedId());

        if (specs[1] == null) {
            m_inputParameterSelectionPanel.setEnabled(false);
            m_hasInputTable = false;
        } else {
            m_inputParameterSelectionPanel.setEnabled(true);
            m_hasInputTable = true;
        }

        if (specs[0] == null) {
            NodeContext nodeContext = NodeContext.getContext();
            WorkflowManager wfm = nodeContext.getWorkflowManager();
            WorkflowContext context = wfm.getContext();
            if (context.isTemporaryCopy()) {
                configureTemporaryCopyWithoutServerConnection();
            } else {
                configureLocalExecution(wfm);
            }
        } else {
            configureRemoteExecution(specs);
        }


        // If we open the dialog a second time and an panelUpdater is currently running (probably waiting
        // for the workflow lock because the workflow to call is already executing) we need to cancel it to avoid
        // filling the panelMap twice

        m_inputParameterSelectionPanel.clearParameters();
        m_outputParameterSelectionPanel.clearParameters();
        m_flowVariableDestination.clearParameters();
        m_flowCredentialsDestination.clearParameters();
        m_selectWorkflowPath.setText(configuration.getWorkflowPath());

        // Have to be set after setText() is called on m_selectWorkflowPath!
        m_configuredInputParameter = configuration.getSelectedInputParameter();
        m_configuredOutputParameter = configuration.getSelectedOutputParameter();
        m_configuredFlowVariableDestination = configuration.getFlowVariableDestination();
        m_configuredFlowCredentialsDestination = configuration.getFlowCredentialsDestination();
    }

    private void configureLocalExecution(final WorkflowManager wfm) {
        m_serverConnection = new LocalExecutionServerConnection(wfm);
        m_serverAddress.setText("No server connection");
        fillWorkflowList();
    }

    private void configureTemporaryCopyWithoutServerConnection() {
        String message = "This node cannot be configured without a server connection in a temporary copy of the"
            + " workflow, please download the workflow to your local workspace for configuration";
        m_stateErrorLabel.setText(message);
        m_serverConnection = null;
        disableAllUIElements();
    }

    private void configureRemoteExecution(final PortObjectSpec[] specs) {
        enableAllUIElements();
        try {
            m_serverConnection = readServerConnection(specs[0],
                Optional.ofNullable(NodeContext.getContext()).map(NodeContext::getWorkflowManager).orElse(null));
            m_stateErrorLabel.setText("");
            m_serverAddress.setText(m_serverConnection.getHost());
            fillWorkflowList();
        } catch (InvalidSettingsException e) {
            getLogger().debug(e.getMessage(), e);
            disableAllUIElements();
            m_stateErrorLabel.setText(e.getMessage());
            m_serverConnection = null;
        }
    }

    abstract IServerConnection readServerConnection(final PortObjectSpec spec,
        WorkflowManager currentWFM) throws InvalidSettingsException;

    private void disableAllUIElements() {
        disableUISelections();
        m_selectWorkflowPath.setEnabled(false);
        m_asyncInvocationChecker.setEnabled(false);
        m_syncInvocationChecker.setEnabled(false);
        m_retainJobOnFailure.setEnabled(false);
    }

    private void enableAllUIElements() {
        enableUISelections();
        m_selectWorkflowPath.setEnabled(true);
        m_asyncInvocationChecker.setEnabled(true);
        m_syncInvocationChecker.setEnabled(true);
        m_retainJobOnFailure.setEnabled(true);
    }

    private void setSelectedWorkflow(final String selectedWorkflow) {
        removeConfiguredSelections();
        m_selectWorkflowPath.setText(selectedWorkflow); // triggers updater
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

    private void updateParameters() {
        if (m_parameterUpdater == null || m_parameterUpdater.isDone() || m_parameterUpdater.cancel(true)) {
            m_parameterUpdater = new ParameterUpdater();
            m_parameterUpdater.execute();
        } else {
            m_workflowErrorLabel.setText("Failed to interrupt analysis of current workflow!");
        }
    }

    private void fillWorkflowList() {
        assert m_workflowLister == null;
        m_workflowErrorLabel.setText("");
        disableUISelectionsWhileFetchingWorkflows();
        m_filteredWorkflowModel = new FilterableListModel(Collections.emptyList());
        m_workflowLister = new ListWorkflowsWorker();
        m_workflowLister.execute();
    }

    private void disableUISelectionsWhileFetchingWorkflows() {
        m_loadingMessage.setText("Fetching workflows...");
        m_loadingAnimation.setVisible(true);
        disableUISelections();
    }

    private void disableUISelections() {
        m_selectWorkflowButton.setEnabled(false);
        m_useFullyQualifiedNamesChecker.setEnabled(false);
        m_inputParameterSelectionPanel.setEnabled(false);
        m_outputParameterSelectionPanel.setEnabled(false);
        m_flowVariableDestination.setEnabled(false);
        m_flowCredentialsDestination.setEnabled(false);
    }

    private void enableUISelectionsAfterFetchingWorkflows() {
        m_loadingMessage.setText(" ");
        m_loadingAnimation.setVisible(false);
        enableUISelections();
    }

    private void enableUISelections() {
        m_selectWorkflowButton.setEnabled(true);
        m_useFullyQualifiedNamesChecker.setEnabled(true);
        m_inputParameterSelectionPanel.setEnabled(m_hasInputTable);
        m_outputParameterSelectionPanel.setEnabled(true);
        m_flowVariableDestination.setEnabled(true);
        m_flowCredentialsDestination.setEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose() {
        if (m_parameterUpdater != null) {
            m_parameterUpdater.cancel(true);
            m_parameterUpdater = null;
        }

        if (m_workflowLister != null) {
            m_workflowLister.cancel(true);
            m_workflowLister = null;
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
                throw e.getCause() instanceof Exception ? (Exception)e.getCause() : e;
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
                m_filteredWorkflowModel = new FilterableListModel(listedWorkflows);
                m_workflowList.setModel(m_filteredWorkflowModel);
            } catch (InterruptedException | CancellationException ex) {
                clearWorkflowList();
                // do nothing
            } catch (Exception ex) {
                clearWorkflowList();
                var pair = ServerConnectionUtil.handle(ex);
                m_workflowErrorLabel.setText(pair.getLeft());
                LOGGER.error(pair.getLeft(), pair.getRight());
            } finally {
                enableUISelectionsAfterFetchingWorkflows();
            }
        }

    }

    private void clearWorkflowList() {
        m_workflowList.setModel(new FilterableListModel(Collections.emptyList()));
    }

    /**
     * Extension of a swing worker that fetches both the input and output values from
     * a workflow backend and then populates the selectable input and output parameter combo boxes
     * of this dialog with the values, maintaining previous selection if present and still valid.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ParameterUpdater extends SwingWorkerWithContext<List<Map<String, JsonValue>>, Void> {

        private final String m_workflowPath;

        ParameterUpdater() {
            m_workflowPath = m_selectWorkflowPath.getText();
            m_workflowErrorLabel.setText("");
        }

        @Override
        protected List<Map<String, JsonValue>> doInBackgroundWithContext() throws Exception {
            CheckUtils.checkSetting(StringUtils.isNotEmpty(m_workflowPath), "No workflow path provided");
            CheckUtils.checkSetting(CallWorkflowConnectionConfiguration.isValidWorkflowPath(m_workflowPath),
                CallWorkflowConnectionConfiguration.invalidWorkflowPathMessage(m_workflowPath));
            CallWorkflowTableNodeConfiguration tempConfig = new CallWorkflowTableNodeConfiguration();
            tempConfig.setLoadTimeout(Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue()));
            tempConfig.setWorkflowPath(m_workflowPath);
            tempConfig.setKeepFailingJobs(false);
            if (m_serverConnection != null) {
                try (IWorkflowBackend backend = m_serverConnection.createWorkflowBackend(tempConfig)) {
                    if (backend != null) {
                        backend.loadWorkflow();
                        // The input nodes needs to be set to make sure the output values are present
                        Map<String, ExternalNodeData> inputNodes = backend.getInputNodes();
                        backend.updateWorkflow(inputNodes);
                        return Arrays.asList(getInputNodeValues(backend), backend.getOutputValues());
                    } else {
                        return null;
                    }
                }
            } else {
                return null;
            }
        }

        private Map<String, JsonValue> getInputNodeValues(final IWorkflowBackend backend) {
            Map<String, JsonValue> inputNodes = new HashMap<>();
            for (Map.Entry<String, ExternalNodeData> e : backend.getInputNodes().entrySet()) {
                JsonValue json = e.getValue().getJSONValue();
                if (json != null) {
                    inputNodes.put(e.getKey(), json);
                }
            }
            return inputNodes;
        }

        @Override
        protected void doneWithContext() {
            if (!isCancelled()) {
                try {
                    List<Map<String, JsonValue>> parameterMaps = get();
                    if (parameterMaps != null) {
                        Map<String, JsonValue> inputNodeData = parameterMaps.get(0);

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
                        Map<String, JsonValue> tableInputs = new HashMap<String, JsonValue>();
                        Map<String, JsonValue> credentialInputs = new HashMap<String, JsonValue>();
                        Map<String, JsonValue> otherInputs = new HashMap<String, JsonValue>();
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
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                        m_outputParameterSelectionPanel.update(outputNodeData, m_configuredOutputParameter);

                        m_validWorkflowPath = true;
                        m_workflowErrorLabel.setText("");
                    }
                } catch (InterruptedException | CancellationException e) {
                    // do nothing
                } catch (Exception e) {
                    m_validWorkflowPath = false;
                    m_inputParameterSelectionPanel.clearParameters();
                    m_outputParameterSelectionPanel.clearParameters();
                    m_flowVariableDestination.clearParameters();

                    Throwable cause = ExceptionUtils.getRootCause(e);

                    var errorPair = ServerConnectionUtil.handle(e);

                    if (!(cause instanceof InvalidSettingsException)) {
                        LOGGER.debug(errorPair.getLeft(), errorPair.getRight());
                    }
                    m_workflowErrorLabel.setText(errorPair.getLeft());
                }
            }
            m_parameterUpdater = null;
        }
    }

    /**
     * Panel holding an info label, a combo box where a parameter selection can be made
     * and a warning label.
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

        ParameterSelection(
                final JLabel infoLabel,
                final JComboBox<ParameterId> parameterSelection,
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
            GridBagConstraints gbc = new GridBagConstraints();
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
            ParameterId alreadySelected = (ParameterId) m_parameterSelection.getSelectedItem();
            resetAndLoad(parameterToNodeData);
            setSelection(alreadySelected, configuredParameter);
        }

        private void resetAndLoad(final Map<String, JsonValue> nodeData) {
            clearSelectionsAndIdMaps();
            List<ParameterId> serviceParameters = getServiceParameters(nodeData);

            if (serviceParameters.isEmpty()) {
                m_parameterSelection.setEnabled(false);
            } else {
                m_parameterSelection.setEnabled(true);
                serviceParameters.sort(ParameterId::compareTo);
                serviceParameters.forEach(parameter -> m_parameterSelection.addItem(parameter));
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
            Map<String, String> fullyQualifiedToSimpleIDMap = IWorkflowBackend.getFullyQualifiedToSimpleIDMap(nodeData.keySet());
            for (Entry<String, JsonValue> parameterToDataEntry : nodeData.entrySet()) {
                String fullyQualifiedId = parameterToDataEntry.getKey();
                String simpleId = fullyQualifiedToSimpleIDMap.get(fullyQualifiedId);
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
                    selectedParameterId = new ParameterId(configuredSelection, m_fullyQualifiedToSimple.get(configuredSelection));
                }
            } else {
                if (m_simpleToFullyQualified.containsKey(configuredSelection)) {
                    selectedParameterId = new ParameterId(m_simpleToFullyQualified.get(configuredSelection), configuredSelection);
                }
            }
            m_parameterSelection.setSelectedItem(selectedParameterId);
            if (m_parameterSelection.getSelectedIndex() == -1) {
                m_warningLabel.setText("Configured parameter \"" + configuredSelection + "\" does not longer exist in the selected workflow.");
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
            return (ParameterId) m_parameterSelection.getSelectedItem();
        }
    }

    /**
     * Custom renderer that decides whether to display the fully qualified id or
     * the simple id of a parameter in the combo-box, based on the users selection.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ParameterRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = -5747012371561384934L;

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(@SuppressWarnings("rawtypes") final JList list,
            final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {

            String text = null;
            if (value instanceof ParameterId) {
                boolean useFullyQualifiedName = m_useFullyQualifiedNamesChecker.isSelected();
                text = ((ParameterId)value).getId(useFullyQualifiedName);
            }

            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }

    /**
     * Dialog that enables the user to select a workflow from an already populated list. Offers a text field which can
     * be used as a filter. When something has been entered in the field only workflow paths containing this entry will
     * be shown in the list.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class SelectWorkflowDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private final JTextField m_filterField;

        SelectWorkflowDialog(final Frame frame) {
            super(frame);
            m_filterField = new JTextField();
            requestFocus();
            setModal(true);
            setLayout(new BorderLayout());
            add(createContentPane(), BorderLayout.CENTER);
            setTitle("Browse workflows");
            pack();
            setLocationRelativeTo(getParent());
            setSize(500, 800);

            getRootPane().registerKeyboardAction(e -> closeDialog(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        private Container createContentPane() {
            JPanel selectWorkflowPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();

            /*
             * Filter text field
             */

            gbc.gridx = 0;
            gbc.gridwidth = 1;
            gbc.gridy++;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            selectWorkflowPanel.add(new JLabel("Search:"), gbc);
            gbc.gridx++;
            gbc.weightx = 1;
            m_filterField.requestFocusInWindow();
            selectWorkflowPanel.add(m_filterField, gbc);
            m_filterField.getDocument().addDocumentListener(new DocumentListener() {

                @Override
                public void removeUpdate(final DocumentEvent e) {
                    update(e);
                }

                @Override
                public void insertUpdate(final DocumentEvent e) {
                    update(e);
                }

                @Override
                public void changedUpdate(final DocumentEvent e) {
                    update(e);
                }

                void update(final DocumentEvent e) {
                    try {
                        final Document doc = e.getDocument();
                        m_filteredWorkflowModel.setFilter(doc.getText(0, doc.getLength()));
                    } catch (BadLocationException e1) {
                        // Will never happen
                    }
                }
            });

            m_filterField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(final KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        m_workflowList.grabFocus();
                    }
                }
            });

            /*
             * Scroll pane with all available workflows
             */

            m_workflowList = new JList<>(m_filteredWorkflowModel);
            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 2;
            gbc.weighty = 1;
            gbc.fill = GridBagConstraints.BOTH;
            final JScrollPane scrollPane = new JScrollPane(m_workflowList);
            selectWorkflowPanel.add(scrollPane, gbc);

            m_workflowList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        setSelectionAndClose();
                    }
                }
            });

            m_workflowList.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(final KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        setSelectionAndClose();
                    }
                }
            });

            /*
             * Control panel
             */

            JButton selectButton = new JButton("Select");
            selectButton.addActionListener(l -> setSelectionAndClose());

            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(l -> closeDialog());

            JPanel controlPanel = new JPanel();

            controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.LINE_AXIS));
            controlPanel.add(Box.createHorizontalGlue());
            controlPanel.add(selectButton, null);
            controlPanel.add(Box.createHorizontalStrut(10));
            controlPanel.add(cancelButton, null);
            controlPanel.add(Box.createHorizontalStrut(5));
            controlPanel.add(Box.createHorizontalGlue());

            gbc.gridx = 0;
            gbc.gridy++;
            gbc.weightx = gbc.weighty = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;
            selectWorkflowPanel.add(controlPanel, gbc);
            return selectWorkflowPanel;
        }

        private void setSelectionAndClose() {
            String selectedWorkflow = m_workflowList.getSelectedValue();
            setSelectedWorkflow(selectedWorkflow);
            closeDialog();
        }

        private void closeDialog() {
            m_filteredWorkflowModel.setFilter("");
            setVisible(false);
            dispose();
        }

    }

}
