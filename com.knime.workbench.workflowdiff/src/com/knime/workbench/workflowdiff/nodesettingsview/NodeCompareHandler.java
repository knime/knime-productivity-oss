package com.knime.workbench.workflowdiff.nodesettingsview;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

public class NodeCompareHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
		if (selection != null & selection instanceof IStructuredSelection) {
			IStructuredSelection strucSelection = (IStructuredSelection) selection;
			if (strucSelection.size() == 2) {
				@SuppressWarnings("unchecked")
				Iterator<Object> iterator = strucSelection.iterator();
				Object firstSel = iterator.next();
				Object secondSel = iterator.next();
				if (firstSel instanceof NodeContainerEditPart && secondSel instanceof NodeContainerEditPart) {
					NodeContainer nc1 = ((NodeContainerEditPart) firstSel).getNodeContainer();
					NodeContainer nc2 = ((NodeContainerEditPart) secondSel).getNodeContainer();
					try {
						NodeSettingsViewer vpart = null;
						vpart = (NodeSettingsViewer) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
								.getActivePage().showView(NodeSettingsViewer.ID);
						vpart.setElements(nc1, nc2);
					} catch (PartInitException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		}
		return null;
	}

}
