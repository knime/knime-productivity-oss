/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright, 2008 - 2013
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
        addPairToPanel("File Path Variable", m_filePathVariableNameCombo,
                panel, gbc);
        addTripelToPanel("Target Location", m_outputURL, browseButton, panel,
                gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(m_overwriteCheckbox, gbc);
        addTab("Options", panel);
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

