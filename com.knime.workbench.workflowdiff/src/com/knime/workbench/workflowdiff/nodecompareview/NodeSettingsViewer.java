package com.knime.workbench.workflowdiff.nodecompareview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.knime.core.node.workflow.NodeContainer;

public class NodeSettingsViewer extends ViewPart {
	public static final String ID = "com.knime.workbench.workflowdiff.nodeSettingsView";

	private NodeSettingsMergeViewer mergeViewer;

	@Override
	public void createPartControl(Composite parent) {
		FillLayout layout = new FillLayout();
		parent.setLayout(layout);
		mergeViewer = new NodeSettingsMergeViewer(parent, SWT.FULL_SELECTION | SWT.BORDER);
		getSite().setSelectionProvider(mergeViewer.getTreeViewer2());
	}

	@Override
	public void setFocus() {
		mergeViewer.getControl().setFocus();
	}

	public void refresh() {
		mergeViewer.refresh();
	}

	public void setElements(Object[] input1, Object[] input2) {
		mergeViewer.setElements(input1, input2);
	}

	public void setElements(NodeContainer nc1, NodeContainer nc2) {
		mergeViewer.setElements(nc1, nc2);
	}

}