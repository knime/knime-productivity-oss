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
 *   Created on 28 Sep 2021 by carlwitt
 */
package org.knime.workflowservices.connection.util;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;

/**
 * Provides controls to edit the values in a {@link CallWorkflowConnectionConfiguration}.
 *
 * Shows the address of the connected server, check boxes for discarding or keeping jobs, and expected job execution
 * durations. See {@link #getMainPanel()}
 *
 * Provides controls for backoff policy parameters: retries, delays, etc. See {@link #getBackoffPanel()}.
 *
 * Provides controls for timeouts: See {@link #getTimeoutControls()}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class CallWorkflowConnectionControls {

    private enum State {
            REMOTE, ERROR, LOCAL
    }

    final Controls m_controls = new Controls();

    /**
     * Just the pure controls.
     *
     * @author Carl Witt, KNIME AG, Zurich, Switzerland
     */
    private static final class Controls {

        /** contains {@link CallWorkflowConnectionControls#getMainPanel()} controls */
        private final JPanel m_panel = new JPanel();

        /** For advanced settings tab: backoff policy parameters for job status polling */
        private final BackoffPanel m_backoffpanel = new BackoffPanel();

        /** For advanced settings tab: create workflow job time out and fetch workflow parameter timeout */
        private final ConnectionTimeoutControls m_timeoutPanel = new ConnectionTimeoutControls();

        private final JPanel m_serverExecution = new JPanel(new GridBagLayout());

        final JLabel m_serverAddress = new JLabel("No server connection");

        final JRadioButton m_syncInvocationChecker =
            new JRadioButton("Short duration: the workflow is expected to run quickly (less than 10 seconds)");

        final JRadioButton m_asyncInvocationChecker =
            new JRadioButton("Long duration: the workflow is expected to run longer than 10 seconds");

        final JCheckBox m_retainJobOnFailure = new JCheckBox("Retain job on failure");

        final JCheckBox m_discardJobOnSuccesfulExecution = new JCheckBox("Discard job on successful execution");

        final JLabel m_errorLabel = new JLabel("");

        /**
         * Shows either a the remote controls (state "REMOTE") or an error message (state "ERROR") or just a simple
         * message that execution is local ("LOCAL").
         */
        private final CardLayout m_cardLayout = new CardLayout();

        public Controls() {
            m_panel.setBorder(BorderFactory.createTitledBorder("KNIME Server Call Settings"));
            m_panel.setLayout(m_cardLayout);

            initRemoteExecutionPanel();

            m_errorLabel.setForeground(Color.red.darker());

            m_panel.add(m_serverExecution, State.REMOTE.toString());
            m_panel.add(m_errorLabel, State.ERROR.toString());
            m_panel.add(new JLabel("Local Workflow Execution"), State.LOCAL.toString());

            setState(State.LOCAL);
        }

        /**
         * @param state
         */
        public void setState(final State state) {
            m_cardLayout.show(m_panel, state.toString());
        }

        private void initRemoteExecutionPanel() {
            final var gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.NONE;

            m_serverExecution.add(m_serverAddress, gbc);

            gbc.gridy++;
            m_serverExecution.add(m_errorLabel, gbc);

            var invocationPanel = createInvocationPanel();
            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 2;
            m_serverExecution.add(invocationPanel, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 1;
            m_serverExecution.add(m_retainJobOnFailure, gbc);

            gbc.gridy++;
            m_serverExecution.add(m_discardJobOnSuccesfulExecution, gbc);
        }

        /**
         * @return panel containing a radio group for selecting sync/async = no polling/polling invocation
         */
        private JPanel createInvocationPanel() {
            var p = new JPanel(new GridBagLayout());

            p.setBorder(BorderFactory.createTitledBorder("Invocation"));

            var gbc = new GridBagConstraints();

            var bg = new ButtonGroup();
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
    }

    /**
     * Creates a new panel with all controls disabled (until a server is connected)
     */
    public CallWorkflowConnectionControls() {
        enableAllUIElements(false);
        setError("Not initialized.");
    }

    /**
     * @return whether the caller waits for the callee workflow to finish or uses polling to determine its status.
     */
    public boolean isSynchronousInvocation() {
        return m_controls.m_syncInvocationChecker.isSelected();
    }

    /**
     * @return whether to keep or delete unsuccessful execution jobs
     */
    public boolean isKeepFailingJobs() {
        return m_controls.m_retainJobOnFailure.isSelected();
    }

    /**
     * @return whether to delete or keep jobs of successful execution jobs
     */
    public boolean isDiscardJobOnSuccessfulExecution() {
        return m_controls.m_discardJobOnSuccesfulExecution.isSelected();
    }

    /**
     * Saves the currently selected options to a configuration.
     *
     * @param configuration to update
     */
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.setSynchronousInvocation(isSynchronousInvocation());
        configuration.setKeepFailingJobs(isKeepFailingJobs());
        configuration.setDiscardJobOnSuccessfulExecution(isDiscardJobOnSuccessfulExecution());

        configuration.setBackoffPolicy(m_controls.m_backoffpanel.getSelectedBackoffPolicy());
    }

    /**
     * Sets the user interface to the state of the given configuration.
     *
     * @param configuration to read state from
     */
    public void loadConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        if (configuration.isSynchronousInvocation()) {
            m_controls.m_syncInvocationChecker.doClick();
        } else {
            m_controls.m_asyncInvocationChecker.doClick();
        }
        m_controls.m_retainJobOnFailure.setSelected(configuration.isKeepFailingJobs());
        m_controls.m_discardJobOnSuccesfulExecution.setSelected(configuration.isDiscardJobOnSuccessfulExecution());

        // load backoff policy
        m_controls.m_backoffpanel
            .setSelectedBackoffPolicy(configuration.getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY));
    }

    /**
     * @param enable whether to enable or disable all controls
     */
    public void enableAllUIElements(final boolean enable) {
        m_controls.m_asyncInvocationChecker.setEnabled(enable);
        m_controls.m_syncInvocationChecker.setEnabled(enable);
        m_controls.m_retainJobOnFailure.setEnabled(enable);
        m_controls.m_discardJobOnSuccesfulExecution.setEnabled(enable);
        m_controls.m_timeoutPanel.setEnabled(enable);
        m_controls.m_backoffpanel.setEnabled(enable);
    }

    /**
     * @return the panel containing the server address, job polling, and job keep/discard settings
     */
    public JPanel getMainPanel() {
        return m_controls.m_panel;
    }

    /**
     * @return a panel that allows to enter parameters of a {@link BackoffPolicy}.
     */
    public BackoffPanel getBackoffPanel() {
        return m_controls.m_backoffpanel;
    }

    /**
     * @return the panel that contains the load workflow and fetch workflow parameter timeout controls
     */
    public ConnectionTimeoutControls getTimeoutControls() {
        return m_controls.m_timeoutPanel;
    }

    /**
     * Sets the execution mode to polling/asynchronous execution and disables the controls to change the execution mode.
     * Used when report generation for the callee workflow is enabled.
     */
    public void forcePolling() {
        enablePollingSelection();
        m_controls.m_asyncInvocationChecker.doClick();
        m_controls.m_syncInvocationChecker.setEnabled(false);
        m_controls.m_asyncInvocationChecker.setEnabled(false);
    }

    /**
     * Enables the controls to change the execution mode.
     */
    public void enablePollingSelection() {
        m_controls.m_syncInvocationChecker.setEnabled(true);
        m_controls.m_asyncInvocationChecker.setEnabled(true);
    }

    /**
     * Updates the server address label. Clears any error that was previously set. Disables the backoff policy and
     * timeout controls if the execution is not remote.
     *
     * @param serverConnection specifies the server's host name.
     * @param isRemoteExecution whether the server connection will lead to remote execution
     */
    public void setServerConnection(final IServerConnection serverConnection, final boolean isRemoteExecution) {
        if (serverConnection == null || !isRemoteExecution) {
            m_controls.m_serverAddress.setText("Local execution");
        } else {
            m_controls.m_serverAddress.setText("Server address: " + serverConnection.getHost());
        }

        m_controls.m_backoffpanel.setEnabled(isRemoteExecution);
        m_controls.m_timeoutPanel.setEnabled(isRemoteExecution);

        m_controls.setState(isRemoteExecution ? State.REMOTE : State.LOCAL);
    }

    /**
     * Hides all controls and shows an error message.
     *
     * @param message
     */
    public void setError(final String message) {
        m_controls.m_errorLabel.setText(message);
        m_controls.setState(State.ERROR);
    }

}