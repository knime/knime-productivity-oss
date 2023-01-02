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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.filehandling.core.connections.FSLocation;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;

/**
 * Provides controls to edit the values in a {@link CallWorkflowConnectionConfiguration}.
 *
 * Shows the address of the connected remote executor, check boxes for discarding or keeping jobs, and expected job execution
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

        private final JPanel m_remoteExecution = new JPanel(new GridBagLayout());

        final JLabel m_remoteExecutorAddress = new JLabel("No remote connection");

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
            m_panel.setBorder(BorderFactory.createTitledBorder("Execution Settings"));
            m_panel.setLayout(m_cardLayout);

            initRemoteExecutionPanel();

            m_errorLabel.setForeground(Color.red.darker());
            m_errorLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            m_panel.add(m_remoteExecution, State.REMOTE.toString());
            m_panel.add(m_errorLabel, State.ERROR.toString());
            m_panel.add(new JLabel("No settings for local workflow execution."), State.LOCAL.toString());

            setState(State.LOCAL);
        }

        /**
         * @param state
         */
        public void setState(final State state) {
            m_cardLayout.show(m_panel, state.toString());
            if(state == State.REMOTE) {
                m_panel.setPreferredSize(new Dimension(600, 200));
                m_panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            } else {
                m_panel.setPreferredSize(new Dimension(600, 50));
                m_panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            }
        }

        private void initRemoteExecutionPanel() {
            final var gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.NONE;

            m_remoteExecution.add(m_remoteExecutorAddress, gbc);

            gbc.gridy++;
            m_remoteExecution.add(m_errorLabel, gbc);

            var invocationPanel = createInvocationPanel();
            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 2;
            m_remoteExecution.add(invocationPanel, gbc);

            gbc.gridx = 0;
            gbc.gridy++;
            gbc.gridwidth = 1;
            m_remoteExecution.add(m_retainJobOnFailure, gbc);

            gbc.gridy++;
            m_remoteExecution.add(m_discardJobOnSuccesfulExecution, gbc);
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
     * Creates a new panel with all controls disabled (until a remote executor is connected)
     */
    public CallWorkflowConnectionControls() {
        enableAllUIElements(false);
        setError("Please execute the KNIME Connector node that provides the remote connection.");
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
     * Saves the currently selected options to a configuration, that is:
     *
     * synchronous invocation, keep failing jobs, discard jobs on successful execution, timeouts, backoff policy
     *
     * @param configuration to update
     */
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.setSynchronousInvocation(isSynchronousInvocation());
        configuration.setKeepFailingJobs(isKeepFailingJobs());
        configuration.setDiscardJobOnSuccessfulExecution(isDiscardJobOnSuccessfulExecution());
        m_controls.m_timeoutPanel.saveToConfiguration(configuration);
        configuration.setBackoffPolicy(m_controls.m_backoffpanel.getSelectedBackoffPolicy());
    }

    /**
     * Sets the user interface to the state of the given configuration.
     *
     * @param configuration to read state from
     */
    public void loadConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        if (configuration.isSynchronousInvocation()) {
            m_controls.m_syncInvocationChecker.setSelected(true);
            m_controls.m_syncInvocationChecker.doClick();
        } else {
            m_controls.m_asyncInvocationChecker.setSelected(true);
            m_controls.m_asyncInvocationChecker.doClick();
        }

        m_controls.m_retainJobOnFailure.setSelected(configuration.isKeepFailingJobs());
        m_controls.m_discardJobOnSuccesfulExecution.setSelected(configuration.isDiscardJobOnSuccessfulExecution());

        m_controls.m_timeoutPanel.loadFromConfiguration(configuration);

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
     * @return the panel containing the remote executor's address, job polling, and job keep/discard settings
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
     * Updates the remote executor address label. Clears any error that was previously set. Disables the backoff policy
     * and timeout controls if the execution is not remote.
     *
     * @param connection connection to the host that executes the callee workflow, can be local or remote. If null, does
     *            nothing.
     */
    @Deprecated
    public void setRemoteConnection(final IServerConnection connection) {
        if (connection == null) {
            return;
        }
        final boolean isRemote = connection.isRemote();
        if (isRemote) {
            m_controls.m_remoteExecutorAddress.setText("Remote execution on" + connection.getHost());
        } else {
            m_controls.m_remoteExecutorAddress.setText("Local execution");
        }

        m_controls.m_backoffpanel.setEnabled(isRemote);
        m_controls.m_timeoutPanel.setEnabled(isRemote);

        m_controls.setState(isRemote ? State.REMOTE : State.LOCAL);
    }

    /**
     * Updates the remote executor address label. Clears any error that was previously set. Disables the backoff policy and
     * timeout controls if the execution is not remote.
     *
     * @param location the file system location.
     */
    public void setRemoteConnection(final FSLocation location) {
        var isRemoteConnection = ConnectionUtil.isRemoteConnection(location.getFSType());
        if (!isRemoteConnection) {
            m_controls.m_remoteExecutorAddress.setText("Local execution");
        } else {
            m_controls.m_remoteExecutorAddress.setText("Remote execution");
        }

        m_controls.m_asyncInvocationChecker.setEnabled(isRemoteConnection);
        m_controls.m_syncInvocationChecker.setEnabled(isRemoteConnection);
        m_controls.m_retainJobOnFailure.setEnabled(isRemoteConnection);
        m_controls.m_discardJobOnSuccesfulExecution.setEnabled(isRemoteConnection);

        m_controls.m_backoffpanel.setEnabled(isRemoteConnection);
        m_controls.m_timeoutPanel.setEnabled(isRemoteConnection);

        m_controls.setState(isRemoteConnection ? State.REMOTE : State.LOCAL);
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