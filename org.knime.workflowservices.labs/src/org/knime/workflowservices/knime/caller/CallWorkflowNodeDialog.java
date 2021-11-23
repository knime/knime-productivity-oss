package org.knime.workflowservices.knime.caller;

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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

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
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.FilterableListModel;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.knime.CalleeWorkflowData;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 *
 * TODO when exchanging callees with different signatures things blow up (out of bounds exception, needs proper handling)
 */
public class CallWorkflowNodeDialog extends NodeDialogPane implements ConfigurableNodeDialog {

    private ModifiableNodeCreationConfiguration m_nodeCreationConfig;

    private CallWorkflowNodeConfiguration m_configuration = new CallWorkflowNodeConfiguration();

    private final ServerSettingsPanel m_serverSettingsPanel;

    private final ParameterMappingPanel m_parameterMappingPanel;

    CallWorkflowNodeDialog(final NodeCreationConfiguration creationConfig) {

        m_nodeCreationConfig = (ModifiableNodeCreationConfiguration)creationConfig;

        // TODO everything below is copied from (fix after finalizing this dialog)
        // org.knime.productivity.callworkflow.table.AbstractCallWorkflowTableNodeDialogPane

        final JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_workflowList = new JList<>(m_filteredWorkflowModel);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;

        m_serverSettingsPanel = new ServerSettingsPanel(gbc);
        gbc.gridwidth = 3;
        panel.add(m_serverSettingsPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        m_selectWorkflowPath = new JTextField();
        m_selectWorkflowPath.setEditable(true);

        m_selectWorkflowPath.setEnabled(true);
        m_selectWorkflowPath.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(final DocumentEvent e) {
                updateParameters();
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                updateParameters();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
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

        m_loadTimeoutSpinner = new JSpinner(
            new SpinnerNumberModel((int)IServerConnection.DEFAULT_LOAD_TIMEOUT.getSeconds(), 0, null, 30));
        ((JSpinner.DefaultEditor)m_loadTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        JLabel timeoutLabel = new JLabel("Load timeout [s]: ");
        panel.add(timeoutLabel, gbc);
        gbc.gridx++;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(m_loadTimeoutSpinner, gbc);
        var timeoutTooltip = "The timeout to use when loading a remote workflow";
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
        var loadingBox = new JPanel();
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

        gbc.gridwidth = 4;
        gbc.weighty = 1;
        gbc.weightx = 1;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;

        m_parameterMappingPanel = new ParameterMappingPanel();
        panel.add(m_parameterMappingPanel, gbc);

        addTab("Callee Workflow", new JScrollPane(panel));

    }

    /**
     * Called when the {@link ParameterUpdateWorker} is done with retrieving information about the callee workflow.
     *
     * @param remoteWorkflowProperties retrieved information, e.g., input ports and output ports.
     */
    private void onWorkflowPropertiesLoad(final CalleeWorkflowProperties remoteWorkflowProperties) {

        // create input/output ports for this node according to the workflow inputs/outputs of the callee
        configureNodePorts(remoteWorkflowProperties);

        // allow the user to select the fetched callee workflow input parameters as targets for this node's ports
        ModifiablePortsConfiguration portsConf = m_nodeCreationConfig.getPortConfig().get();

        m_parameterMappingPanel.update(remoteWorkflowProperties,
            portsConf.getGroup(CallWorkflowNodeFactory.INPUT_PORT_GROUP),
            portsConf.getGroup(CallWorkflowNodeFactory.OUTPUT_PORT_GROUP));

        m_configuration.setCalleeWorkflowProperties(remoteWorkflowProperties);

        // mark retrieval as done
        m_parameterUpdater = null;
    }

    /**
     * Configures the input and output ports of the node such that they reflect the input/output ports of the callee
     * workflow.
     *
     * Called in {@link #onWorkflowPropertiesLoad(CalleeWorkflowProperties)}, triggered by the asynchronous completion
     * of the {@link ParameterUpdateWorker} execution
     *
     * @param properties describes the input and output ports of the callee workflow.
     */
    private void configureNodePorts(final CalleeWorkflowProperties properties) {

        Optional<ModifiablePortsConfiguration> portConfig = m_nodeCreationConfig.getPortConfig();
        if (portConfig.isEmpty()) {
            return; // should never happen - setCurrentNodeCreationConfiguration would be broken
        }

        // update input ports
        ExtendablePortGroup inputConfig =
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflowNodeFactory.INPUT_PORT_GROUP);
        while (inputConfig.hasConfiguredPorts()) {
            inputConfig.removeLastPort();
        }
        for (CalleeWorkflowData portDesc : properties.getInputNodes()) {
            inputConfig.addPort(portDesc.getPortType());
        }

        // update output ports
        ExtendablePortGroup outputConfig =
            (ExtendablePortGroup)portConfig.get().getGroup(CallWorkflowNodeFactory.OUTPUT_PORT_GROUP);
        while (outputConfig.hasConfiguredPorts()) {
            outputConfig.removeLastPort();
        }
        for (CalleeWorkflowData portDesc : properties.getOutputNodes()) {
            outputConfig.addPort(portDesc.getPortType());
        }

        m_portConfigChanged = true;
    }

    @Override
    protected final void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        CheckUtils.checkSetting(m_parameterUpdater == null, "Can't apply configuration while analysis is ongoing");

        m_serverSettingsPanel.saveConfiguration(m_configuration);
        m_configuration.setWorkflowPath(m_selectWorkflowPath.getText());
        m_configuration.setLoadTimeout(Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue()));

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

        // configure remote execution if a server connection is present
        var fileSystemPOS = (FileSystemPortObjectSpec)specs[0];
        var nodeContext = NodeContext.getContext();
        var wfm = nodeContext.getWorkflowManager();
        WorkflowContext context = wfm.getContext();

        if (context.isTemporaryCopy()) {
            configureTemporaryCopyWithoutServerConnection();
        } else {
            configureRemoteExecution(fileSystemPOS, wfm);
        }

        if (m_configuration.getCalleeWorkflowProperties().isPresent()) {
            ModifiablePortsConfiguration portsConf = m_nodeCreationConfig.getPortConfig().get();
            m_parameterMappingPanel.update(m_configuration.getCalleeWorkflowProperties().get(),
                portsConf.getGroup(CallWorkflowNodeFactory.INPUT_PORT_GROUP),
                portsConf.getGroup(CallWorkflowNodeFactory.OUTPUT_PORT_GROUP));
        }

        // If we open the dialog a second time and an panelUpdater is currently running (probably waiting
        // for the workflow lock because the workflow to call is already executing) we need to cancel it to avoid
        // filling the panelMap twice
        //  TODO understand the concurrency problem solved here
        //        m_parameterMappingPanel.clearParameters();

        m_selectWorkflowPath.setText(m_configuration.getWorkflowPath());
    }

    static final NodeLogger LOGGER = NodeLogger.getLogger(CallWorkflowNodeDialog.class);

    /**
     *
     * TODO everything below is copied from {@link AbstractCallWorkflowTableNodeDialogPane}
     *
     */

    private IServerConnection m_serverConnection;

    private final JTextField m_selectWorkflowPath;

    private final JSpinner m_loadTimeoutSpinner;

    private final JButton m_selectWorkflowButton;

    private final JLabel m_workflowErrorLabel;

    private final JLabel m_stateErrorLabel;

    private ParameterUpdateWorker m_parameterUpdater;

    private final JProgressBar m_loadingAnimation;

    private final JLabel m_loadingMessage;

    private JList<String> m_workflowList;

    private SwingWorkerWithContext<List<String>, Void> m_workflowLister;

    private FilterableListModel m_filteredWorkflowModel = new FilterableListModel(Collections.emptyList());

    /**
     * Set to true after the node's ports have been adapted to a callee workflow's in- and output ports.
     *
     * @see #configureNodePorts(NodeCreationConfiguration)
     */
    private boolean m_portConfigChanged = false;

    private void openSelectWorkflowDialog() {
        var selectWorkflowDialog = new SelectWorkflowDialog(getParentFrame());
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

    private void configureTemporaryCopyWithoutServerConnection() {
        String message = "This node cannot be configured without a server connection in a temporary copy of the"
            + " workflow, please download the workflow to your local workspace for configuration";
        m_stateErrorLabel.setText(message);
        m_serverConnection = null;
        disableAllUIElements();
    }

    private void configureRemoteExecution(final FileSystemPortObjectSpec spec, final WorkflowManager wfm) {
        enableAllUIElements();
        try {
            m_serverConnection = ServerConnectionUtil.getConnection(spec, wfm);
            m_stateErrorLabel.setText("");
            m_serverSettingsPanel.m_serverAddress.setText(m_serverConnection.getHost());
            fillWorkflowList();
        } catch (InvalidSettingsException e) {
            getLogger().debug(e.getMessage(), e);
            disableAllUIElements();
            m_stateErrorLabel.setText(e.getMessage());
            m_serverConnection = null;
        }
    }

    private void disableAllUIElements() {
        disableUISelections();
        m_selectWorkflowPath.setEnabled(false);
        // TODO or only m_asyncInvocationChecker, m_syncInvocationChecker, and m_retainJobOnFailure?
        m_serverSettingsPanel.setEnabled(false);
    }

    private void enableAllUIElements() {
        enableUISelections();
        m_selectWorkflowPath.setEnabled(true);
        // TODO or only m_asyncInvocationChecker, m_syncInvocationChecker, and m_retainJobOnFailure?
        m_serverSettingsPanel.setEnabled(true);
    }

    private void setSelectedWorkflow(final String selectedWorkflow) {
        // TODO why was this necessary?
        //        m_parameterMappingPanel.removeConfiguredSelections();
        // TODO trigger the event not the triggerer?
        m_selectWorkflowPath.setText(selectedWorkflow); // triggers updater
    }

    private void updateParameters() {

        var inProgress =
            !(m_parameterUpdater == null || m_parameterUpdater.isDone() || m_parameterUpdater.cancel(true));

        if (m_selectWorkflowPath.getText().isBlank() || inProgress) {
            m_workflowErrorLabel.setText("Failed to interrupt analysis of current workflow!");
            return;
        }

        m_parameterMappingPanel.removeAll();
        m_parameterMappingPanel.revalidate();
        m_parameterMappingPanel.repaint();

        var timeOut = Duration.ofSeconds(((Number)m_loadTimeoutSpinner.getValue()).intValue());
        m_parameterUpdater = new ParameterUpdateWorker(//
            m_selectWorkflowPath.getText(), //
            m_workflowErrorLabel::setText, //
            timeOut, //
            m_serverConnection, //
            this::onWorkflowPropertiesLoad, CallWorkflowNodeConfiguration::new);

        m_configuration.setCalleeWorkflowProperties(null);
        m_parameterUpdater.execute();

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
        m_parameterMappingPanel.setEnabled(false);

    }

    private void enableUISelectionsAfterFetchingWorkflows() {
        m_loadingMessage.setText(" ");
        m_loadingAnimation.setVisible(false);
        enableUISelections();
    }

    private void enableUISelections() {
        m_selectWorkflowButton.setEnabled(true);
        m_parameterMappingPanel.setEnabled(true);
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

    /**
     * To provide up-to-date information about the port configuration of the node before opening the dialog.
     * {@inheritDoc}
     */
    @Override
    public void setCurrentNodeCreationConfiguration(final ModifiableNodeCreationConfiguration nodeCreationConfig) {
        m_nodeCreationConfig = nodeCreationConfig;
    }

    /**
     * TODO This is not called when the node is in executed state and thus will occasionally not update its ports.
     * getNewNodeCreationConfiguration is called as part of WrappedNodeDialog#m_postApplyDialogAction. Problem is that
     * the post apply action is called in WrappedNodeDialog#doApply ONLY if the node is not executed. This means that a
     * Call Workflow node that is still green from the previous execution won't update its ports when closing the dialog
     * by clicking "Ok". Since WrappedNodeDialog#doOK is called before the underlying node dialog's saveSettings I'm a
     * little confused how to fix this. See also the NodeContainerEditPart#postApplyDialogAction that issues a
     * ReplaceNodePortCommand for {@link NodeDialogPane}s that implement {@link ConfigurableNodeDialog} (like this
     * class) I think the WorkflowExecutorNodeModel doesn't have this problem because it consumes the workflow to
     * execute from an input port and thus can not be in executed state after the workflow to be executed changed
     * {@inheritDoc}
     */
    @Override
    public Optional<ModifiableNodeCreationConfiguration> getNewNodeCreationConfiguration() {
        return Optional.ofNullable(m_portConfigChanged ? m_nodeCreationConfig : null);
    }

}
