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
