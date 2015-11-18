package com.knime.workbench.workflowdiff.handlers;

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
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileInfo;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.view.IFileStoreProvider;

import com.knime.licenses.LicenseChecker;
import com.knime.licenses.LicenseException;
import com.knime.licenses.LicenseFeatures;
import com.knime.licenses.LicenseUtil;
import com.knime.workbench.workflowdiff.editor.WorkflowCompareConfiguration;
import com.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput;

public class SnapshotViewContextMenuHandler extends AbstractHandler {

	private static final LicenseChecker LICENSE_CHECKER = new LicenseUtil(LicenseFeatures.WorkflowDiff);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (!checkLicense()) {
			return null;
		}

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
					return null;
				}
				AbstractExplorerFileInfo fileInfo1 = file1.fetchInfo();
				if (strucSelection.size() == 2) {
					element = iterator.next();
					if (element instanceof IFileStoreProvider) {
						file2 = ((IFileStoreProvider) element).getFileStore();
					} else if (element instanceof AbstractExplorerFileStore) {
						file2 = (AbstractExplorerFileStore) element;
					} else {
						return null;
					}
				}
				AbstractExplorerFileInfo fileInfo2 = file2.fetchInfo();
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

	private boolean checkLicense() {
		try {
			LICENSE_CHECKER.checkLicense();
			return true;
		} catch (LicenseException ex) {
			MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_WARNING | SWT.OK);
			box.setMessage("Workflow diff not available: " + ex.getMessage());
			box.setText("Workflow diff not available");
			box.open();
			return false;
		}
	}
}
