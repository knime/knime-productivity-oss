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
				if (element instanceof IFileStoreProvider) {
					IFileStoreProvider snapshot = (IFileStoreProvider) element;
					IFileStoreProvider snapshot2 = snapshot;
					if (strucSelection.size() == 2) {
						element = iterator.next();
						if (element instanceof IFileStoreProvider) {
							snapshot2 = (IFileStoreProvider) element;
						}
					}
					WorkflowCompareConfiguration conf = new WorkflowCompareConfiguration();
					conf.setRightEditable(false);
					conf.setLeftEditable(false);
					WorkflowCompareEditorInput input = new WorkflowCompareEditorInput(snapshot.getFileStore(),
							snapshot2.getFileStore(), conf);
					CompareUI.openCompareEditor(input);
				}
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
