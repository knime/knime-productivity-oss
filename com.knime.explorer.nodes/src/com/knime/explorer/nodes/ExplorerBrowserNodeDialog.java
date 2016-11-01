/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright by 
  * KNIME.com, Zurich, Switzerland
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
 *   25.10.2011 (morent): created
 */
package com.knime.explorer.nodes;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.port.PortObjectSpec;

/**
 * <code>NodeDialog</code> for the "ExplorerWriter" Node.
 * Allows to write to locations mounted in KNIME explorer
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more
 * complex dialog please derive directly from
 * {@link org.knime.core.node.NodeDialogPane}.
 *
 * @author Dominik Morent, KNIME.com AG, Zurich, Switzerland
 */
public class ExplorerBrowserNodeDialog extends AbstractExplorerLocationSelectionNodeDialog {
    private final JTextField m_outputFilename;
    private final JTextField m_outputURL;

    /**
     * New pane for configuring ExplorerWriter node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected ExplorerBrowserNodeDialog() {
        m_outputURL = new JTextField();
        m_outputURL.setColumns(20);
        m_outputURL.setMaximumSize(new Dimension(Integer.MAX_VALUE, m_outputURL.getMaximumSize().height));
        m_outputFilename = new JTextField();
        m_outputFilename.setColumns(20);
        m_outputFilename.setMaximumSize(new Dimension(Integer.MAX_VALUE, m_outputFilename.getMaximumSize().height));
        final JButton browseButton = createBrowseButton(m_outputURL);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Output Filename"), gbc);
        gbc.gridx++;
        gbc.weightx = 1;
        panel.add(m_outputFilename, gbc);
        gbc.weightx = 0;

        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new JLabel("Target Location"), gbc);
        gbc.gridx++;
        gbc.weightx = 1;
        panel.add(m_outputURL, gbc);
        gbc.weightx = 0;
        gbc.gridx++;
        panel.add(browseButton, gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(new JPanel(), gbc);

        addTab("Settings", panel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        ExplorerBrowserNodeSettings config = new ExplorerBrowserNodeSettings();
        config.loadSettingsInDialog(settings);
        m_outputFilename.setText(config.getFilename());
        m_outputURL.setText(config.getOutputURL());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings)
            throws InvalidSettingsException {
        ExplorerBrowserNodeSettings s = new ExplorerBrowserNodeSettings();
        String url = m_outputURL.getText();
        if (url != null && !url.isEmpty()) {
            s.setOutputURL(url);
        }
        String filename = m_outputFilename.getText();
        if (filename != null && !filename.isEmpty()) {
            s.setFilename(filename);
        }
        s.saveSettingsTo(settings);
    }
}

