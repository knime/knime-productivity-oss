/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   25.10.2011 (morent): created
 */
package org.knime.explorer.nodes;

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

