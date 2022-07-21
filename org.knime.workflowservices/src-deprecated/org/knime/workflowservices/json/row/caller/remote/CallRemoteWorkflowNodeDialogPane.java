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
package org.knime.workflowservices.json.row.caller.remote;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.StringHistoryPanel;
import org.knime.core.node.util.VerticalCollapsablePanels;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.node.workflow.Credentials;
import org.knime.core.node.workflow.ICredentials;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.BackoffPanel;
import org.knime.workflowservices.connection.util.BackoffPolicy;
import org.knime.workflowservices.connection.util.ConnectionTimeoutPanel;
import org.knime.workflowservices.json.row.caller.CallWorkflowNodeModel;
import org.knime.workflowservices.json.row.caller.JSONInputPanel;

/**
 * Dialog to node.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 * @deprecated use {@link CallWorkflowRowBasedNodeDialog}, it unifies local and remote row-based execution
 */
@Deprecated(since = "4.7")
final class CallRemoteWorkflowNodeDialogPane extends NodeDialogPane {
    private final JComboBox<String> m_workflowPath;

    private final JLabel m_errorLabel;

    private final VerticalCollapsablePanels m_collapsablePanels;

    private final Map<String, JSONInputPanel> m_panelMap;

    private final StringHistoryPanel m_hostField;

    private final JTextField m_usernameField;

    private final JPasswordField m_passwordField;

    private final JButton m_listWorkflowButton;

    private final JRadioButton m_useCredentials = new JRadioButton("Use credentials");

    private final JRadioButton m_usePassword = new JRadioButton("Use username & password");

    private final JComboBox<String> m_credentials = new JComboBox<>();

    private final JRadioButton m_syncInvocationChecker;

    private final JRadioButton m_asyncInvocationChecker;

    private final JCheckBox m_createReportChecker;

    private final JComboBox<RptOutputFormat> m_reportFormatCombo;

    private final JCheckBox m_useFullyQualifiedParameterNamesChecker;

    private DataTableSpec m_spec;

    private final CallRemoteWorkflowConfiguration m_settings = new CallRemoteWorkflowConfiguration();

    private final ConnectionTimeoutPanel m_connectionTimeoutPanel = new ConnectionTimeoutPanel();

    private final BackoffPanel m_backoffPanel = new BackoffPanel();

    CallRemoteWorkflowNodeDialogPane() {
        final JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_hostField = new StringHistoryPanel("com.knime.server.nodes.callworkflow");
        m_hostField.setPrototypeDisplayValue(
            "https://very.long.server-name.at.company.com:8080/com.knime.enterprise.webportal/rest");
        m_usernameField = new JTextField();
        m_passwordField = new JPasswordField();

        m_syncInvocationChecker = new JRadioButton(
            "Short duration: the workflow is expected to run quickly (less than 10 seconds)");
        m_asyncInvocationChecker = new JRadioButton(
            "Long duration: the workflow is expected to run longer than 10 seconds");

        m_listWorkflowButton = new JButton("List available workflows");

        m_workflowPath = new JComboBox<>();
        m_workflowPath.setEditable(true);
        m_workflowPath.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(final ItemEvent e) {
                clearInputPanel(p);
            }
        });

        m_workflowPath.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void keyTyped(final KeyEvent e) {
                clearInputPanel(p);
            }
        });

        m_errorLabel = new JLabel();
        m_errorLabel.setForeground(Color.RED.darker());

        m_createReportChecker = new JCheckBox("Create Report");
        m_reportFormatCombo = new JComboBox<>(ArrayUtils.removeElement(RptOutputFormat.values(), RptOutputFormat.HTML));
        m_createReportChecker.addItemListener(e -> {
            boolean runReport = m_createReportChecker.isSelected();
            m_reportFormatCombo.setEnabled(runReport);
            if (runReport) {
                m_asyncInvocationChecker.doClick();
                m_syncInvocationChecker.setEnabled(false);
                m_asyncInvocationChecker.setEnabled(false);
            } else {
                m_syncInvocationChecker.setEnabled(true);
                m_asyncInvocationChecker.setEnabled(true);
            }
        });
        m_createReportChecker.doClick();

        m_collapsablePanels = new VerticalCollapsablePanels();
        m_panelMap = new LinkedHashMap<>();

        m_useFullyQualifiedParameterNamesChecker =
                new JCheckBox("Use Fully Qualified Name for Input and Output Parameters");
        m_useFullyQualifiedParameterNamesChecker.addItemListener(l -> {
            final boolean isUseFullyQualifiedID = m_useFullyQualifiedParameterNamesChecker.isSelected();
            m_panelMap.values().stream().forEach(panel -> panel.setUseFullyqualifiedID(isUseFullyQualifiedID));
        });

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.gridx = gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        p.add(new JLabel("Server address: "), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        p.add(m_hostField, gbc);

        JPanel authPanel = createAuthenticationPanel();
        authPanel.setBorder(BorderFactory.createTitledBorder("Authentication"));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        p.add(authPanel, gbc);

        JPanel invocationPanel = createInvocationPanel();
        invocationPanel.setBorder(BorderFactory.createTitledBorder("Invocation"));
        gbc.gridy++;
        p.add(invocationPanel, gbc);

        m_listWorkflowButton.addActionListener(e -> fillWorkflowList());
        gbc.gridx = 1;
        gbc.gridy += 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        p.add(m_listWorkflowButton, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        p.add(new JLabel("Workflow Path: "), gbc);

        gbc.gridx += 1;
        gbc.weightx = 1.0;
        p.add(m_workflowPath, gbc);

        JButton b = new JButton("Load input format");
        b.addActionListener(e -> updatePanels());
        gbc.gridx++;
        gbc.weightx = 0;
        p.add(b, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy += 1;
        p.add(m_errorLabel, gbc);

        gbc.gridy += 1;
        gbc.fill = GridBagConstraints.BOTH;
        p.add(ViewUtils.getInFlowLayout(m_createReportChecker, m_reportFormatCombo, new JLabel("   "),
            m_useFullyQualifiedParameterNamesChecker), gbc);

        gbc.gridy += 1;
        gbc.weighty = 1.0;
        p.add(m_collapsablePanels, gbc);

        addTab("Workflow", p);
        addTab("Advanced Settings", createAdvancedTab());
    }

    private JPanel createAdvancedTab() {
        final JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(m_connectionTimeoutPanel);
        container.add(m_backoffPanel);
        container.add(Box.createHorizontalGlue());
        return container;
    }

    private JPanel createAuthenticationPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        p.add(m_usePassword, gbc);

        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.gridy++;
        gbc.insets = new Insets(5, 22, 5, 5);
        final JLabel usernameLabel = new JLabel("User: ");
        p.add(usernameLabel, gbc);

        gbc.gridx += 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        p.add(m_usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy += 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        final JLabel passwordLabel = new JLabel("Password: ");
        p.add(passwordLabel, gbc);

        gbc.gridx += 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        p.add(m_passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy += 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        p.add(m_useCredentials, gbc);

        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        gbc.gridy++;
        gbc.insets = new Insets(5, 22, 5, 5);
        p.add(m_credentials, gbc);

        ButtonGroup bg = new ButtonGroup();
        bg.add(m_useCredentials);
        bg.add(m_usePassword);

        m_useCredentials.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent e) {
                m_credentials.setEnabled(m_useCredentials.isSelected());
                usernameLabel.setEnabled(!m_useCredentials.isSelected());
                m_usernameField.setEnabled(!m_useCredentials.isSelected());
                passwordLabel.setEnabled(!m_useCredentials.isSelected());
                m_passwordField.setEnabled(!m_useCredentials.isSelected());
            }
        });

        m_useCredentials.doClick();

        return p;
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

    private ICredentials getCredentials() {
        if (m_useCredentials.isSelected()) {
            if (m_credentials.getSelectedItem() == null) {
                m_errorLabel.setText("No credentials select, cannot get workflow list.");
                return null;
            }
            ICredentials creds = getCredentialsProvider().get((String)m_credentials.getSelectedItem());
            if (creds == null) {
                m_errorLabel.setText("No credentials with name '" + m_credentials.getSelectedItem()
                    + "' found, cannot get workflow list.");
                return null;
            } else {
                return creds;
            }
        } else if (m_usernameField.getText().isEmpty()) {
            m_errorLabel.setText("No username provided, cannot get workflow list.");
            return null;
        } else if (m_passwordField.getPassword().length == 0) {
            m_errorLabel.setText("No password provided, cannot get workflow list.");
            return null;
        } else {
            return new Credentials("XXX", m_usernameField.getText(), new String(m_passwordField.getPassword()));
        }
    }

    private void fillWorkflowList() {
        final ICredentials creds;

        if (m_hostField.getSelectedString().isEmpty()) {
            m_errorLabel.setText("No hostname provided, cannot get workflow list.");
        } else if ((creds = getCredentials()) != null) {
            m_errorLabel.setText("");
            getPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            m_listWorkflowButton.setEnabled(false);

            SwingWorkerWithContext<List<String>, Void> worker = new SwingWorkerWithContext<List<String>, Void>() {
                @Override
                protected List<String> doInBackgroundWithContext() throws Exception {
                    try (var serverConnection = newServerConnection(creds)) {
                        return serverConnection.listWorkflows();
                    }
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                protected void doneWithContext() {
                    try {
                        m_workflowPath.removeAllItems();
                        for (String s : get()) {
                            m_workflowPath.addItem(s);
                        }
                        m_workflowPath.setSelectedItem(m_settings.getWorkflowPath());
                    } catch (InterruptedException ex) {
                        // do nothing
                    } catch (Exception ex) {
                        handleRemoteError(ex);
                    } finally {
                        getPanel().setCursor(Cursor.getDefaultCursor());
                        m_listWorkflowButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }
    }

    private void handleRemoteError(final Exception ex) {
        var pair = ServerConnectionUtil.handle(ex);
        m_errorLabel.setText(pair.getLeft());
        NodeLogger.getLogger(CallRemoteWorkflowNodeDialogPane.class).error(pair.getLeft(), pair.getRight());

    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.setWorkflowPath((String)m_workflowPath.getSelectedItem());
        m_settings.setRemoteHostAndPort(m_hostField.getSelectedString());
        m_settings.setUsername(m_usernameField.getText());
        m_settings.setPassword(new String(m_passwordField.getPassword()));
        m_settings.setLoadTimeout(m_connectionTimeoutPanel.getSelectedLoadTimeout());
        m_settings.setConnectTimeout(m_connectionTimeoutPanel.getSelectedConnectionTimeout());
        m_settings.setReadTimeout(m_connectionTimeoutPanel.getSelectedReadTimeout());
        m_settings.setBackoffPolicy(m_backoffPanel.getSelectedBackoffPolicy());

        m_hostField.commitSelectedToHistory();

        if (m_useCredentials.isSelected()) {
            m_settings.setCredentialsName((String)m_credentials.getSelectedItem());
        } else {
            m_settings.setCredentialsName(null);
        }
        m_settings.setSynchronousInvocation(m_syncInvocationChecker.isSelected());

        RptOutputFormat reportFormatOrNull;
        if (m_createReportChecker.isSelected()) {
            reportFormatOrNull = (RptOutputFormat)m_reportFormatCombo.getSelectedItem();
        } else {
            reportFormatOrNull = null;
        }
        final boolean isUseFullyQualifiedIDs = m_useFullyQualifiedParameterNamesChecker.isSelected();
        m_settings.setUseQualifiedParameterNames(isUseFullyQualifiedIDs);
        JSONInputPanel.saveSettingsTo(m_settings, isUseFullyQualifiedIDs, m_panelMap.values());
        m_settings.setReportFormatOrNull(reportFormatOrNull);
        m_settings.save(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {
        m_settings.loadInDialog(settings);
        m_hostField.updateHistory();
        m_hostField.setSelectedString(m_settings.getRemoteHostAndPort());

        m_connectionTimeoutPanel.setSelectedLoadTimeout(
                m_settings.getLoadTimeout().orElse(IServerConnection.DEFAULT_LOAD_TIMEOUT)
        );
        m_connectionTimeoutPanel.setSelectedConnectionTimeout(
                m_settings.getConnectTimeout().orElse(IServerConnection.DEFAULT_TIMEOUT)
        );
        m_connectionTimeoutPanel.setSelectedReadTimeout(
                m_settings.getReadTimeout().orElse(IServerConnection.DEFAULT_TIMEOUT)
        );

        m_backoffPanel.setSelectedBackoffPolicy(
                m_settings.getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY)
        );

        m_usernameField.setText(m_settings.getUsername());
        m_passwordField.setText(m_settings.getPassword());

        m_credentials.removeAllItems();
        for (String s : getCredentialsProvider().listNames()) {
            if (CallWorkflowNodeModel.verifyCredentialIdentifier(s)) {
                m_credentials.addItem(s);
            }
        }

        if (m_settings.getCredentialsName() == null) {
            m_usePassword.doClick();
        } else {
            m_useCredentials.doClick();
            m_credentials.setSelectedItem(m_settings.getCredentialsName());
        }

        if (m_settings.isSynchronousInvocation()) {
            m_syncInvocationChecker.doClick();
        } else {
            m_asyncInvocationChecker.doClick();
        }

        m_spec = specs[0];

        m_workflowPath.setSelectedItem(m_settings.getWorkflowPath());
        RptOutputFormat reportFormatOrNull = m_settings.getReportFormatOrNull();
        if ((reportFormatOrNull != null) != m_createReportChecker.isSelected()) {
            m_createReportChecker.doClick();
        }
        m_reportFormatCombo.setSelectedItem(reportFormatOrNull != null
                ? reportFormatOrNull : m_reportFormatCombo.getModel().getElementAt(0));
        m_useFullyQualifiedParameterNamesChecker.setSelected(m_settings.isUseQualifiedParameterNames());

        updatePanels();

        JSONInputPanel.loadSettingsFrom(m_settings, m_panelMap, m_spec);
    }

    private void updatePanels() {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");

        ICredentials creds;
        if (StringUtils.isEmpty((CharSequence)m_workflowPath.getSelectedItem())) {
            m_errorLabel.setText("No workflow path provided");
        } else if ((creds = getCredentials()) != null) {
            try (var serverConnection = newServerConnection(creds); var backend = newBackend(serverConnection)) {
                backend.loadWorkflow();
                Map<String, ExternalNodeData> inputNodes = backend.getInputNodes();
                Map<String, String> fullyQualifiedToSimpleIDMap =
                        IWorkflowBackend.getFullyQualifiedToSimpleIDMap(inputNodes.keySet());

                for (Map.Entry<String, ExternalNodeData> entry : inputNodes.entrySet()) {
                    var fullyQualifiedID = entry.getKey();
                    var simpleID = fullyQualifiedToSimpleIDMap.get(fullyQualifiedID);
                    var p = new JSONInputPanel(simpleID, fullyQualifiedID,
                        entry.getValue().getJSONValue(), m_spec);
                    p.setUseFullyqualifiedID(m_useFullyQualifiedParameterNamesChecker.isSelected());
                    m_panelMap.put(entry.getKey(), p);
                    m_collapsablePanels.addPanel(p, false);
                }
            } catch (Exception ex) {
                 handleRemoteError(ex);
            }
        }

        JPanel panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();

    }

    private IServerConnection newServerConnection(final ICredentials creds) throws InvalidSettingsException {
        final var hostAndPort = m_hostField.getSelectedString();
        final var username = creds.getLogin();
        final var password = creds.getPassword();
        return ServerConnectionUtil.getConnection(hostAndPort,
            username, password, m_connectionTimeoutPanel.getSelectedConnectionTimeout(),
            m_connectionTimeoutPanel.getSelectedReadTimeout());
    }

    private IWorkflowBackend newBackend(final IServerConnection serverConnection) throws Exception {
        final var callConfig = new CallWorkflowConnectionConfiguration() //
                .setBackoffPolicy(m_backoffPanel.getSelectedBackoffPolicy()) //
                .setDiscardJobOnSuccessfulExecution(true) //
                .setKeepFailingJobs(false) //
                .setLoadTimeout(m_connectionTimeoutPanel.getSelectedLoadTimeout()) //
                .setSynchronousInvocation(true) //
                .setWorkflowPath((String)m_workflowPath.getSelectedItem());
        return serverConnection.createWorkflowBackend(callConfig);
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        m_spec = null;
    }

    private void clearInputPanel(final JPanel p) {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");
        m_collapsablePanels.revalidate();
        p.revalidate();
        getPanel().repaint();
    }
}
