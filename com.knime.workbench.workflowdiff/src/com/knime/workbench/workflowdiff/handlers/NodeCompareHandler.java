package com.knime.workbench.workflowdiff.handlers;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.editor2.WorkflowEditor;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

import com.knime.licenses.LicenseChecker;
import com.knime.licenses.LicenseException;
import com.knime.licenses.LicenseFeatures;
import com.knime.licenses.LicenseUtil;
import com.knime.workbench.workflowdiff.nodecompareview.NodeSettingsViewer;

public class NodeCompareHandler extends AbstractHandler {
	
	private static final NodeLogger LOGGER = NodeLogger.getLogger(NodeCompareHandler.class);
    private static final LicenseChecker LICENSE_CHECKER = new LicenseUtil(LicenseFeatures.WorkflowDiff);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		if (!checkLicense()) {
			return null;
		}
		ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
		if (selection != null && selection instanceof IStructuredSelection) {
			IStructuredSelection strucSelection = (IStructuredSelection) selection;
			if (strucSelection.size() == 2) {
				@SuppressWarnings("unchecked")
				Iterator<Object> iterator = strucSelection.iterator();
				Object firstSel = iterator.next();
				Object secondSel = iterator.next();
				if (firstSel instanceof NodeContainerEditPart && secondSel instanceof NodeContainerEditPart) {
					try {
						((NodeSettingsViewer) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
								.showView(NodeSettingsViewer.ID))
										.setElements(
												(WorkflowEditor) HandlerUtil.getActiveWorkbenchWindow(event)
														.getActivePage().getActiveEditor(),
												(NodeContainerEditPart) firstSel, (NodeContainerEditPart) secondSel);
					} catch (Exception e) {
						LOGGER.error("Unable to open node compare view", e);
					}
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
