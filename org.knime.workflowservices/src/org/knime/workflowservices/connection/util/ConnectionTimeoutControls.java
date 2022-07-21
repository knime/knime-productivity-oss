package org.knime.workflowservices.connection.util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;

/**
 * UI to customize different timeouts used when communicating with a server.
 *
 * Loads and saves from/to {@link CallWorkflowConnectionConfiguration}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class ConnectionTimeoutControls  {

    /** Contains all controls. */
    private final JPanel m_panel = new JPanel();

    /**
     * @see CallWorkflowConnectionConfiguration#getLoadTimeout()
     */
    JSpinner m_loadTimeoutSpinner = createSpinner((int)IServerConnection.DEFAULT_LOAD_TIMEOUT.getSeconds());

    /**
     * @see CallWorkflowConnectionConfiguration#getFetchParameterTimeout()
     */
    JSpinner m_fetchWorkflowParameterTimeout = createSpinner((int)IServerConnection.DEFAULT_TIMEOUT.getSeconds());

    /**
     * Create controls. Use {@link #getPanel()} to add to containing dialog.
     */
    public ConnectionTimeoutControls() {
        m_panel.setBorder(BorderFactory.createTitledBorder("Connection timeouts"));

        m_panel.setLayout(new GridBagLayout());

        final var gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(5, 0, 5, 5);

        addLabeledSpinner("Workflow load timeout",
            "The timeout to use when initializing the remote execution of a workflow", m_loadTimeoutSpinner, gbc);

        addLabeledSpinner("Fetch workflow parameters timeout",
            "The timeout to use when fetching the input and output parameters of a remote workflow",
            m_fetchWorkflowParameterTimeout, gbc);

        m_panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, (int)m_panel.getPreferredSize().getHeight()));
    }

    private void addLabeledSpinner(final String label, final String tooltip, final JSpinner spinner,
        final GridBagConstraints gbc) {
        gbc.gridy++;
        gbc.gridx = 0;

        gbc.weightx = 0;
        var labelBox = Box.createHorizontalBox();
        labelBox.add(new JLabel(label));
        labelBox.add(Box.createHorizontalStrut(10));
        m_panel.add(labelBox, gbc);

        gbc.gridx++;
        gbc.weightx = 1;
        m_panel.add(spinner, gbc);

        labelBox.setToolTipText(tooltip);
        spinner.setToolTipText(tooltip);
    }

    private static JSpinner createSpinner(final int value) {
        var spinner = new JSpinner(new SpinnerNumberModel(value, 0, null, 30));
        ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().setColumns(4);
        return spinner;
    }

    /**
     * @return value see {@link CallWorkflowConnectionConfiguration#getLoadTimeout()}
     */
    public Duration getSelectedLoadTimeout() {
        return getSelectedDurationOf(m_loadTimeoutSpinner);
    }

    /**
     * @return value see {@link CallWorkflowConnectionConfiguration#getFetchParametersTimeout()}
     */
    public Duration getSelectedFetchParametersTimeout() {
        return getSelectedDurationOf(m_fetchWorkflowParameterTimeout);
    }

    /**
     * Update the UI to the given state.
     *
     * @param configuration to load from.
     */
    public void loadFromConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.getLoadTimeout().ifPresent(d -> m_loadTimeoutSpinner.setValue(d.getSeconds()));
        configuration.getFetchParametersTimeout()
            .ifPresent(d -> m_fetchWorkflowParameterTimeout.setValue(d.getSeconds()));
    }

    /**
     * Write the UI state to the configuration.
     *
     * @param configuration to write to.
     */
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.setLoadTimeout(getSelectedDurationOf(m_loadTimeoutSpinner));
        configuration.setFetchParametersTimeout(getSelectedDurationOf(m_fetchWorkflowParameterTimeout));
    }

    /**
     * @param spinner to get from
     * @return the value in seconds
     */
    private static Duration getSelectedDurationOf(final JSpinner spinner) {
        return Duration.ofSeconds(((Number)spinner.getValue()).intValue());
    }

    public void setEnabled(final boolean enabled) {
        // do not call super, otherwise it greys out the border title (and only that)
        Arrays.stream(m_panel.getComponents()).forEach(c -> c.setEnabled(enabled));
    }

    /**
     * @return the panel
     */
    public JPanel getPanel() {
        return m_panel;
    }


}
