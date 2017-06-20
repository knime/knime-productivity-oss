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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.FlowVariableListCellRenderer;
import org.knime.core.node.workflow.FlowVariable;

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
public class ExplorerWriterNodeDialog
        extends AbstractExplorerLocationSelectionNodeDialog {
    private final JComboBox m_filePathVariableNameCombo;
    private final JTextField m_outputURL;
    private final JCheckBox m_overwriteCheckbox;

    /**
     * New pane for configuring ExplorerWriter node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected ExplorerWriterNodeDialog() {
        m_filePathVariableNameCombo = new JComboBox(new DefaultComboBoxModel());
        m_filePathVariableNameCombo.setRenderer(
                new FlowVariableListCellRenderer());
        m_outputURL = new JTextField();
        m_outputURL.setColumns(20);
        final JButton browseButton = createBrowseButton(m_outputURL);
        m_overwriteCheckbox = new JCheckBox("Overwrite existing files", false);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("File Path Variable"), gbc);
        gbc.gridx++;
        gbc.weightx = 1;
        panel.add(m_filePathVariableNameCombo, gbc);
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
        gbc.gridx = 1;
        panel.add(m_overwriteCheckbox, gbc);

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
        DefaultComboBoxModel m =
                (DefaultComboBoxModel)m_filePathVariableNameCombo.getModel();
        ExplorerWriterNodeSettings config = new ExplorerWriterNodeSettings();
        config.loadSettingsInDialog(settings);
        String selectedName = config.getFilePathVariableName();
        FlowVariable selectedVar = null;
        m.removeAllElements();
        for (FlowVariable v : getAvailableFlowVariables().values()) {
            if (v.getType().equals(FlowVariable.Type.STRING)) {
                m.addElement(v);
                if (v.getName().equals(selectedName)) {
                    selectedVar = v;
                }
            }
        }
        if (selectedVar != null) {
            m_filePathVariableNameCombo.setSelectedItem(selectedVar);
        }
        m_outputURL.setText(config.getOutputURL());
        m_overwriteCheckbox.setSelected(config.isOverwriteOK());
    }

    /**
     * @return the name of the file path flow variable
     */
    String getFilePathVariableName() {
        return ((FlowVariable)m_filePathVariableNameCombo.getSelectedItem())
                .getStringValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings)
            throws InvalidSettingsException {
        FlowVariable v =
            (FlowVariable)m_filePathVariableNameCombo.getSelectedItem();
        ExplorerWriterNodeSettings s = new ExplorerWriterNodeSettings();
        if (v != null) {
            s.setFilePathVariableName(v.getName());
        }
        String url = m_outputURL.getText();
        if (url != null && !url.isEmpty()) {
            s.setOutputURL(url);
        }
        s.setOverwriteOK(m_overwriteCheckbox.isSelected());
        s.saveSettingsTo(settings);
    }
}

