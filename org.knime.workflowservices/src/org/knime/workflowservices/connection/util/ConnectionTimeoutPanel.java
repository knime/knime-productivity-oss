package org.knime.workflowservices.connection.util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.knime.workflowservices.connection.IServerConnection;

/**
 * UI to customise different timeouts used when communicating with a server.
 */
@SuppressWarnings("serial")
public class ConnectionTimeoutPanel extends JPanel {

    JSpinner m_loadTimeoutSpinner = createSpinner((int)IServerConnection.DEFAULT_LOAD_TIMEOUT.getSeconds());

    JSpinner m_connectionTimeoutSpinner = createSpinner((int)IServerConnection.DEFAULT_TIMEOUT.getSeconds());

    JSpinner m_readTimeoutSpinner = createSpinner((int)IServerConnection.DEFAULT_TIMEOUT.getSeconds());

    @SuppressWarnings("java:S1699") // calling public methods in constructor
    public ConnectionTimeoutPanel() {
        this.setBorder(BorderFactory.createTitledBorder("Connection timeouts (in seconds)"));

        this.setLayout(new GridBagLayout());

        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(5, 0, 5, 5);

        addLabeledSpinner("Workflow load timeout", "The timeout to use when loading a remote workflow",
            m_loadTimeoutSpinner, gbc);

        addLabeledSpinner("Server connection timeout",
            "The timeout to use when establishing a connection to the server", m_connectionTimeoutSpinner, gbc);

        addLabeledSpinner("Server read timeout", "The timeout to use when waiting for a response from the server",
            m_readTimeoutSpinner, gbc);

        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int)this.getPreferredSize().getHeight()));
    }

    private void addLabeledSpinner(final String label, final String tooltip, final JSpinner spinner,
        final GridBagConstraints gbc) {
        gbc.gridy++;
        gbc.gridx = 0;

        gbc.weightx = 0;
        Box labelBox = Box.createHorizontalBox();
        labelBox.add(new JLabel(label));
        labelBox.add(Box.createHorizontalStrut(10));
        this.add(labelBox, gbc);

        gbc.gridx++;
        gbc.weightx = 1;
        this.add(spinner, gbc);

        labelBox.setToolTipText(tooltip);
        spinner.setToolTipText(tooltip);
    }

    private static JSpinner createSpinner(final int value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0, null, 30));
        ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().setColumns(4);
        return spinner;
    }

    public Duration getSelectedLoadTimeout() {
        return getSelectedDurationOf(m_loadTimeoutSpinner);
    }

    public Duration getSelectedConnectionTimeout() {
        return getSelectedDurationOf(m_connectionTimeoutSpinner);
    }

    public Duration getSelectedReadTimeout() {
        return getSelectedDurationOf(m_readTimeoutSpinner);
    }

    private static Duration getSelectedDurationOf(final JSpinner spinner) {
        return Duration.ofSeconds(((Number)spinner.getValue()).intValue());
    }

    public void setSelectedLoadTimeout(final Duration timeout) {
        m_loadTimeoutSpinner.setValue(timeout.getSeconds());
    }

    public void setSelectedConnectionTimeout(final Duration timeout) {
        m_connectionTimeoutSpinner.setValue(timeout.getSeconds());
    }

    public void getSelectedReadTimeout(final Duration timeout) {
        m_readTimeoutSpinner.setValue(timeout.getSeconds());
    }
}
