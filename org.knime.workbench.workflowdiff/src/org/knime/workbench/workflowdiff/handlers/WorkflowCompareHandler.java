/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 */
package org.knime.workbench.workflowdiff.handlers;

import java.util.Iterator;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.handlers.HandlerUtil;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileInfo;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.view.IFileStoreProvider;
import org.knime.workbench.workflowdiff.editor.WorkflowCompareConfiguration;
import org.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput;

public class WorkflowCompareHandler extends AbstractHandler {

	private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowCompareHandler.class);

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
        if (selection != null & selection instanceof IStructuredSelection) {
            IStructuredSelection strucSelection = (IStructuredSelection) selection;
            if (strucSelection.size() == 1 || strucSelection.size() == 2) {
                Iterator<?> iterator = strucSelection.iterator();
                Object element = iterator.next();
                AbstractExplorerFileStore file1;
                AbstractExplorerFileStore file2;
                if (element instanceof IFileStoreProvider) {
                    file1 = ((IFileStoreProvider) element).getFileStore();
                    file2 = file1;
                } else if (element instanceof AbstractExplorerFileStore) {
                    file1 = (AbstractExplorerFileStore) element;
                    file2 = file1;
                } else {
                	LOGGER.coding("Invalid object type in selection, not able to perform diff");
                    return null;
                }
                AbstractExplorerFileInfo fileInfo1 = file1.fetchInfo();
                if (strucSelection.size() == 2 || fileInfo1.isSnapshot()) {
                    element = strucSelection.size() == 2 ? iterator.next() : file1.getParent();
                    if (element instanceof IFileStoreProvider) {
                        file2 = ((IFileStoreProvider)element).getFileStore();
                    } else if (element instanceof AbstractExplorerFileStore) {
                        file2 = (AbstractExplorerFileStore)element;
                    } else {
                        LOGGER.coding("Invalid object type in selection, not able to perform diff");
                        return null;
                    }
                }
                AbstractExplorerFileInfo fileInfo2 = file2.fetchInfo();
                if (fileInfo1.isReservedSystemItem() || fileInfo2.isReservedSystemItem()) {
                    deletedWorkflowPopup();
                    return null;
                }
                boolean file1Valid = fileInfo1.isWorkflow() || fileInfo1.isWorkflowTemplate() || fileInfo1.isMetaNode()
                    || fileInfo1.isSnapshot();
                boolean file2Valid = fileInfo2.isWorkflow() || fileInfo2.isWorkflowTemplate() || fileInfo2.isMetaNode()
                    || fileInfo2.isSnapshot();
                if (!(file1Valid && file2Valid)) {
                    MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_WARNING | SWT.OK);
                    box.setMessage("Please select only workflows, workflow-templates, meta-nodes or snapshots.");
                    box.setText("Workflow diff not available");
                    box.open();
                    return null;
                }

                WorkflowCompareConfiguration conf = new WorkflowCompareConfiguration();
                conf.setRightEditable(false);
                conf.setLeftEditable(false);
                WorkflowCompareEditorInput input = new WorkflowCompareEditorInput(file1, file2, conf);
                CompareUI.openCompareEditor(input);
            }
        }
        return null;
    }
    
    private static void deletedWorkflowPopup() {
        MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_WARNING | SWT.OK);
        box.setMessage("Cannot compare with a deleted workflow.");
        box.setText("Workflow diff not available");
        box.open();
    }
}
