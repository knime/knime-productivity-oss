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
package org.knime.workflowservices.knime.caller;

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

final class ServerSettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    JLabel m_serverAddress;

    JRadioButton m_syncInvocationChecker;

    JRadioButton m_asyncInvocationChecker;

    JCheckBox m_retainJobOnFailure;

    JCheckBox m_discardJobOnSuccesfulExecution;

    /**
     * @param gbc
     */
    ServerSettingsPanel(final GridBagConstraints gbc) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("KNIME Server Call Settings"));
        add(new JLabel("Server address:"), gbc);

        gbc.gridx++;
        m_serverAddress = new JLabel("No server connected");

        add(m_serverAddress, gbc);

        m_syncInvocationChecker =
            new JRadioButton("Short duration: the workflow is expected to run quickly (less than 10 seconds)");
        m_asyncInvocationChecker =
            new JRadioButton("Long duration: the workflow is expected to run longer than 10 seconds");
        JPanel invocationPanel = createInvocationPanel();
        invocationPanel.setBorder(BorderFactory.createTitledBorder("Invocation"));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        add(invocationPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 1;
        m_retainJobOnFailure = new JCheckBox("Retain job on failure");
        add(m_retainJobOnFailure, gbc);

        gbc.gridy++;
        m_discardJobOnSuccesfulExecution = new JCheckBox("Discard job on successful execution");
        add(m_discardJobOnSuccesfulExecution, gbc);
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

    /**
     * @param configuration
     */
    public void saveConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.setSynchronousInvocation(m_syncInvocationChecker.isSelected());
        configuration.setKeepFailingJobs(m_retainJobOnFailure.isSelected());
        configuration.setDiscardJobOnSuccessfulExecution(m_discardJobOnSuccesfulExecution.isSelected());
    }

    /**
     * @param configuration
     */
    public void loadConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        if (configuration.isSynchronousInvocation()) {
            m_syncInvocationChecker.doClick();
        } else {
            m_asyncInvocationChecker.doClick();
        }

        m_retainJobOnFailure.setSelected(configuration.isKeepFailingJobs());
        m_discardJobOnSuccesfulExecution.setSelected(configuration.isDiscardJobOnSuccessfulExecution());
    }
}