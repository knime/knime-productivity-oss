
package com.knime.workbench.workflowdiff.actions;

import java.util.Iterator;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

public class EditorTestAction implements IObjectActionDelegate {
    public static final String TEST_ACTION_ID = "com.knime.workbench.workflowdiff.actions.EditorTestAction";

    private static final NodeLogger LOGGER = NodeLogger.getLogger(EditorTestAction.class);
    
    private IStructuredSelection m_selection = StructuredSelection.EMPTY;
    
    private IStructuredSelection getSelection() {
    	return m_selection;
	}
    
    private boolean canRun() {
        IStructuredSelection selection = getSelection();
        return selection.size() == 2;
    }

    public void run() {
        IStructuredSelection selection = getSelection();
        if (selection.size() != 2) {
            Assert.isTrue(false);
            return;
        }
        
        Iterator<?> selIter = selection.iterator();
        Object first = selIter.next();
        Object second = selIter.next();
        if ((first instanceof NodeContainerEditPart) && (second instanceof NodeContainerEditPart)) {
        	NodeContainerEditPart f = (NodeContainerEditPart) first;
        	NodeContainerEditPart s = (NodeContainerEditPart) second;
        	LOGGER.error("Selected: Node1: " + f.getWorkflowManager().getID() + ", Node2:" + s.getWorkflowManager().getID());
        } else {
        	LOGGER.error("Works only with two selected elements.");
        }
    }

	@Override
	public void run(IAction action) {
		if (action.isEnabled()) {
			run();
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		if (selection != null && (selection instanceof IStructuredSelection)) {
			m_selection = (IStructuredSelection) selection;
		} else {
			m_selection = StructuredSelection.EMPTY;
		}
		boolean canRun = canRun();
		LOGGER.error("Can run: " + canRun);
		action.setEnabled(canRun);
	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}
}
