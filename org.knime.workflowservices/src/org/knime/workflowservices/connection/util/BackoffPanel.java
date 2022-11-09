package org.knime.workflowservices.connection.util;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * UI component that provides input of configuration values for retrying with backoff delay.
 */
@SuppressWarnings("serial")
public final class BackoffPanel extends JPanel {
    private final JSpinner m_baseSpinner = createSpinner(BackoffPolicy.DEFAULT_BACKOFF_POLICY.getBase(), 50);

    private final JSpinner m_multiplierSpinner =
        createSpinner(BackoffPolicy.DEFAULT_BACKOFF_POLICY.getMultiplier(), 1);

    private final JSpinner m_retriesSpinner =
        createSpinner(BackoffPolicy.DEFAULT_BACKOFF_POLICY.getRetries(), 1);

    private static JSpinner createSpinner(final Number value, final int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value.intValue(), 0, null, step));
        ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().setColumns(4);
        return spinner;
    }

    /**
     * Initialise the panel by adding components.
     */
    @SuppressWarnings("java:S1699") // calling public methods in constructor
    public BackoffPanel() {
        this.setBorder(BorderFactory.createTitledBorder("Job Status Polling"));
        this.setLayout(new GridBagLayout());

        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.weightx = gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.insets = new Insets(5, 0, 5, 5);

        addLabeledSpinner("Base timeout (milliseconds)",
            "The base timeout to use when polling for the job status during asynchronous invokation.", m_baseSpinner,
            gbc);

        addLabeledSpinner("Multiplier", "The multiplier applied in each backoff iteration.", m_multiplierSpinner, gbc);

        addLabeledSpinner("Maximum number of retries",
            "The number of times to retry, with incrementally increased backoff.", m_retriesSpinner, gbc);

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

    @Override
    public void setEnabled(final boolean enabled) {
        // do not call super, otherwise it greys out the border title (and only that)
        Arrays.stream(getComponents()).forEach(c -> c.setEnabled(enabled));
    }

    /**
     * Set the values of the UI controls to the given <code>backoffPolicy</code>
     *
     * @param backoffPolicy
     */
    public void setSelectedBackoffPolicy(final BackoffPolicy backoffPolicy) {
        m_baseSpinner.setValue(backoffPolicy.getBase());
        m_multiplierSpinner.setValue(backoffPolicy.getMultiplier());
        m_retriesSpinner.setValue(backoffPolicy.getRetries());
    }

    /**
     * @return A new {@link BackoffPolicy} based on the values of the UI controls.
     */
    public BackoffPolicy getSelectedBackoffPolicy() {
        long base = ((Number)m_baseSpinner.getValue()).longValue();
        long multiplier = ((Number)m_multiplierSpinner.getValue()).longValue();
        int retries = ((Number)m_retriesSpinner.getValue()).intValue();
        return new BackoffPolicy(base, multiplier, retries);
    }
}
