package com.knime.workbench.workflowdiff.handlers;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.editor2.WorkflowEditor;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

import com.knime.workbench.workflowdiff.nodecompareview.NodeSettingsViewer;

public class NodeCompareHandler extends AbstractHandler {
	
	private static final NodeLogger LOGGER = NodeLogger.getLogger(NodeCompareHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
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

}
