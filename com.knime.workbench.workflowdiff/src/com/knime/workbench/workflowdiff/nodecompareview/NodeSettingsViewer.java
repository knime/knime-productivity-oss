package com.knime.workbench.workflowdiff.nodecompareview;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.workbench.editor2.WorkflowEditor;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

import com.knime.workbench.workflowdiff.editor.filters.NodeDiffClearFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilter;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilterContribution;

public class NodeSettingsViewer extends ViewPart {

	public static final String ID = "com.knime.workbench.workflowdiff.nodeSettingsView";

	private NodeSettingsMergeViewer m_mergeViewer;
	private WorkflowEditor m_editor;
	private NodeContainerEditPart m_leftNCEP;
	private NodeContainerEditPart m_rightNCEP;
	private Action[] toolbarActions;

	@Override
	public void createPartControl(Composite parent) {
		FillLayout layout = new FillLayout();
		parent.setLayout(layout);
		m_mergeViewer = new NodeSettingsMergeViewer(parent, SWT.FULL_SELECTION | SWT.BORDER);

		createToolbar();

		getSite().setSelectionProvider(m_mergeViewer.getTreeViewer2());
	}

	private void createToolbar() {
		// get Toolbar
		IActionBars actionBars = getViewSite().getActionBars();
		IToolBarManager toolBar = actionBars.getToolBarManager();
		toolbarActions = new Action[2];

		// Search Field
		NodeDiffFilterContribution searchTextField = new NodeDiffFilterContribution(new NodeDiffFilter(),
				m_mergeViewer.getTreeViewer1(), m_mergeViewer.getTreeViewer2());
		toolBar.add(searchTextField);
		
		//Clear Search Button
		toolBar.add(new NodeDiffClearFilterButton(searchTextField));

		// Select Nodes in Workflow Action
		Action syncAction = new SyncNodeCompareAction(this);
		toolBar.add(syncAction);
		syncAction.setEnabled(false);
		toolbarActions[0] = syncAction;

		// Refresh Action
		Action refreshAction = new RefreshNodeCompareAction(this);
		toolBar.add(refreshAction);
		refreshAction.setEnabled(false);
		toolbarActions[1] = refreshAction;
	}

	@Override
	public void setFocus() {
		m_mergeViewer.getControl().setFocus();
	}

	public void refresh() {
		m_mergeViewer.refresh();
	}

	public void setElements(Object[] input1, Object[] input2) {
		m_mergeViewer.setElements(input1, input2);
	}

	public void setElements(NodeContainer nc1, NodeContainer nc2) {
		m_mergeViewer.setElements(nc1, nc2);
	}

	public void setElements(WorkflowEditor editor, NodeContainerEditPart leftNCEP, NodeContainerEditPart rightNCEP) {
		m_editor = editor;
		m_leftNCEP = leftNCEP;
		m_rightNCEP = rightNCEP;
		m_mergeViewer.setElements(leftNCEP.getNodeContainer(), rightNCEP.getNodeContainer());
		for (int i = 0; i < toolbarActions.length; i++) {
			toolbarActions[i].setEnabled(true);
		}
	}

	public WorkflowEditor getEditor() {
		return m_editor;
	}

	public NodeContainerEditPart getLeftNCEP() {
		return m_leftNCEP;
	}

	public NodeContainerEditPart getRightNCEP() {
		return m_rightNCEP;
	}

}