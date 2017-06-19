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


package com.knime.explorer.nodes;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.util.ThreadUtils;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.dialogs.SpaceResourceSelectionDialog;
import org.knime.workbench.explorer.dialogs.Validator;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.view.AbstractContentProvider;

/**
 *
 * @author Dominik Morent, KNIME.com, Zurich, Switzerland
 *
 */
public abstract class AbstractExplorerLocationSelectionNodeDialog extends
        NodeDialogPane {
    /**
     * Creates a button that allows all non-local explorer mount points and
     * updates the provided text field with the URL of the selected
     * workflow group.
     *
     * @param textField the text field to update with the string value
     *      retrieved from the browsing dialog
     * @return the browse button with attached action listener
     */
    protected JButton createBrowseButton(final JTextField textField) {
        final JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(new ActionListener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                Display.getDefault().syncExec(ThreadUtils.runnableWithContext(new Runnable() {
                    @Override
                    public void run() {
                        // collect all non-local mount ids
                        List<String> mountIDs = new ArrayList<String>();
                        for (Map.Entry<String, AbstractContentProvider> entry
                                : ExplorerMountTable
                                .getMountedContent().entrySet()) {
                            String mountID = entry.getKey();
                            AbstractContentProvider acp = entry.getValue();
                            if (acp.canHostDataFiles()) {
                                mountIDs.add(mountID);
                            }

                        }

                        SpaceResourceSelectionDialog dialog =
                                new SpaceResourceSelectionDialog(Display
                                        .getDefault().getActiveShell(),
                                        mountIDs.toArray(new String[0]), null);
                        dialog.setTitle("Target workflow group selection");
                        dialog.setDescription(
                                "Please select the location to write to.");
                        dialog.setValidator(WorkflowGroupSelectionValidator
                                .getInstance());

                        if (Window.OK == dialog.open()) {
                            String url = dialog.getSelection().toString();
                            if (!url.endsWith("/")) {
                                url += "/";
                            }
                            textField.setText(url);
                        }
                    }
                }));
            }
        });
        return browseButton;
    }

    /**
     * Adds a panel sub-component to the dialog.
     *
     * @param label The label (left hand column)
     * @param c The component (right hand column)
     * @param panelWithGBLayout Panel to add
     * @param gbc constraints.
     */
    protected final void addPairToPanel(final String label, final JComponent c,
            final JPanel panelWithGBLayout, final GridBagConstraints gbc) {
        gbc.gridwidth = 1;
        panelWithGBLayout.add(new JLabel(label), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panelWithGBLayout.add(c, gbc);
    }

    /**
     * Adds a panel sub-component to the dialog.
     *
     * @param label The label (left hand column)
     * @param middle The component (middle column)
     * @param right The component (right hand column)
     * @param panelWithGBLayout Panel to add
     * @param gbc constraints.
     */
    protected final void addTripelToPanel(final String label,
            final JComponent middle, final JComponent right,
            final JPanel panelWithGBLayout, final GridBagConstraints gbc) {
        gbc.gridwidth = 1;
        panelWithGBLayout.add(new JLabel(label), gbc);
        gbc.gridwidth = 1;
        panelWithGBLayout.add(middle, gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panelWithGBLayout.add(right, gbc);
    }

    /**
     * Allows only to select workflow groups in the {@link SpaceResourceSelectionDialog}.
     *
     * @author Dominik Morent, KNIME.com, Zurich, Switzerland
     *
     */
    protected final static class WorkflowGroupSelectionValidator extends Validator {
        private WorkflowGroupSelectionValidator() {
            // hide constructor of singleton
        }

        private static final WorkflowGroupSelectionValidator INSTANCE =
                new WorkflowGroupSelectionValidator();

        /**
         * @return the single instance of this validator
         */
        public static WorkflowGroupSelectionValidator getInstance() {
            return INSTANCE;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String validateSelectionValue(final AbstractExplorerFileStore selection, final String name) {
            return AbstractExplorerFileStore.isWorkflowGroup(selection) ? null
                    : "Only workflow groups can be selected as target.";
        }
    }

}
