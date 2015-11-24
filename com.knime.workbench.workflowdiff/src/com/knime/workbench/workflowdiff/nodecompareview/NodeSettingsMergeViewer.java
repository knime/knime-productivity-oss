package com.knime.workbench.workflowdiff.nodecompareview;

import java.util.ArrayList;
import java.util.ResourceBundle;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.ContentMergeViewer;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.WorkflowManager;

import com.knime.workbench.workflowdiff.editor.WorkflowCompareConfiguration;

public class NodeSettingsMergeViewer extends ContentMergeViewer {

	private NodeSettingsTreeViewer m_treeViewerLeft;
	private NodeSettingsTreeViewer m_treeViewerRight;
	private NodeSettingsTreeViewer m_treeViewerAncestor;

	protected NodeSettingsMergeViewer(int style, ResourceBundle bundle, CompareConfiguration cc) {
		super(style, bundle, cc);
		// TODO Auto-generated constructor stub
	}

	protected NodeSettingsMergeViewer(Composite parent, int style) {
		super(style, ResourceBundle.getBundle("org.eclipse.compare.contentmergeviewer.TextMergeViewerResources"),
				new WorkflowCompareConfiguration());
		buildControl(parent);
		getCompareConfiguration().setRightEditable(false);
		getCompareConfiguration().setLeftEditable(false);
	}

	@Override
	protected void createControls(Composite composite) {
		m_treeViewerLeft = new NodeSettingsTreeViewer(composite,
				SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		m_treeViewerRight = new NodeSettingsTreeViewer(composite,
				SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		m_treeViewerAncestor = new NodeSettingsTreeViewer(composite, SWT.NONE);
		m_treeViewerLeft.addTreeListener(new ITreeViewerListener() {

			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				NodeSettingsItem elem = (NodeSettingsItem) event.getElement();
				m_treeViewerRight.expandToLevel(elem, 1);
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent event) {
				NodeSettingsItem elem = (NodeSettingsItem) event.getElement();
				m_treeViewerRight.collapseToLevel(elem, 1);
			}
		});
		m_treeViewerRight.addTreeListener(new ITreeViewerListener() {

			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				NodeSettingsItem elem = (NodeSettingsItem) event.getElement();
				m_treeViewerLeft.expandToLevel(elem, 1);
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent event) {
				NodeSettingsItem elem = (NodeSettingsItem) event.getElement();
				m_treeViewerLeft.collapseToLevel(elem, 1);
			}
		});
	}

	@Override
	protected void handleResizeAncestor(int x, int y, int width, int height) {
		if (width > 0) {
			m_treeViewerAncestor.setVisible(true);
			m_treeViewerAncestor.setBounds(x, y, width, height);
		} else {
			m_treeViewerAncestor.setVisible(false);
		}
	}

	@Override
	protected void handleResizeLeftRight(int x, int y, int width1, int centerWidth, int width2, int height) {
		m_treeViewerLeft.setBounds(x, y, width1, height);
		m_treeViewerRight.setBounds(x + width1 + centerWidth, y, width2, height);
	}

	@Override
	protected void updateContent(Object ancestor, Object left, Object right) {
		m_treeViewerLeft.refresh();
		m_treeViewerRight.refresh();
	}

	@Override
	protected void copy(boolean leftToRight) {

	}

	@Override
	protected byte[] getContents(boolean left) {
		// TODO Auto-generated method stub
		return null;
	}

	protected NodeSettingsTreeViewer getTreeViewer1() {
		return m_treeViewerLeft;
	}

	protected NodeSettingsTreeViewer getTreeViewer2() {
		return m_treeViewerRight;
	}

	public Control getControl() {
		return m_treeViewerLeft.getControl();
	}

	public void setInput(Object input) {
		m_treeViewerLeft.setInput(input);
	}

	public void setElements(NodeContainer nc1, NodeContainer nc2) {
		WorkflowCompareConfiguration compareConfig = (WorkflowCompareConfiguration) getCompareConfiguration();
		compareConfig.setLeftLabel(nc1.getNameWithID());
		compareConfig.setRightLabel(nc2.getNameWithID());
		WorkflowManager wfm1 = nc1.getParent();
		WorkflowManager wfm2 = nc2.getParent();
		Config modelSettings1 = null;
		Config modelSettings2 = null;
		Config memorySettings1 = null;
		Config memorySettings2 = null;
		Config jobMgrSettings1 = null;
		Config jobMgrSettings2 = null;
		NodeSettings nodeSettings1 = new NodeSettings("Props");
		NodeSettings nodeSettings2 = new NodeSettings("Props");
		NodeSettingsContentProvider contProv1 = (NodeSettingsContentProvider) m_treeViewerLeft.getContentProvider();
		NodeSettingsContentProvider contProv2 = (NodeSettingsContentProvider) m_treeViewerRight.getContentProvider();
		try {
			wfm1.saveNodeSettings(nc1.getID(), nodeSettings1);
			wfm2.saveNodeSettings(nc2.getID(), nodeSettings2);
			if (nodeSettings1.containsKey("model")) {
				modelSettings1 = nodeSettings1.getNodeSettings("model");
			}
			if (nodeSettings2.containsKey("model")) {
				modelSettings2 = nodeSettings2.getNodeSettings("model");
			}
			if (nodeSettings1.containsKey(Node.CFG_MISC_SETTINGS)) {
				memorySettings1 = nodeSettings1.getNodeSettings(Node.CFG_MISC_SETTINGS);
			}
			if (nodeSettings2.containsKey(Node.CFG_MISC_SETTINGS)) {
				memorySettings2 = nodeSettings2.getNodeSettings(Node.CFG_MISC_SETTINGS);
			}
			if (nodeSettings1.containsKey("job.manager")) {
				jobMgrSettings1 = nodeSettings1.getNodeSettings("job.manager");
			}
			if (nodeSettings2.containsKey("job.manager")) {
				jobMgrSettings2 = nodeSettings2.getNodeSettings("job.manager");
			}
		} catch (InvalidSettingsException e) {
			return;
		}
		ArrayList<NodeSettingsItem> input1 = new ArrayList<NodeSettingsItem>(3);
		ArrayList<NodeSettingsItem> input2 = new ArrayList<NodeSettingsItem>(3);
		if(modelSettings1!=null){
			NodeSettingsItem modelItem1 = new NodeSettingsItem("Node Settings", "", "");
			contProv1.addAllConfigValues(modelItem1, modelSettings1);
			input1.add(modelItem1);
		}
		if(modelSettings2!=null){
			NodeSettingsItem modelItem2 = new NodeSettingsItem("Node Settings", "", "");
			contProv2.addAllConfigValues(modelItem2, modelSettings2);
			input2.add(modelItem2);
		}
		if (memorySettings1 != null) {
			NodeSettingsItem memoryItem = new NodeSettingsItem("System Node Settings", "", "");
			contProv1.addAllConfigValues(memoryItem, memorySettings1);
			input1.add(memoryItem);
		}
		if (memorySettings2 != null) {
			NodeSettingsItem memoryItem = new NodeSettingsItem("System Node Settings", "", "");
			contProv2.addAllConfigValues(memoryItem, memorySettings2);
			input2.add(memoryItem);
		}
		if (jobMgrSettings1 != null) {
			NodeSettingsItem jobMgrItem = new NodeSettingsItem("Job Manager Settings", "", "");
			contProv1.addAllConfigValues(jobMgrItem, jobMgrSettings1);
			input1.add(jobMgrItem);
		}
		if (jobMgrSettings2 != null) {
			NodeSettingsItem jobMgrItem = new NodeSettingsItem("Job Manager Settings", "", "");
			contProv2.addAllConfigValues(jobMgrItem, jobMgrSettings2);
			input2.add(jobMgrItem);
		}
		contProv1.setElements(input1.toArray(new NodeSettingsItem[input1.size()]));
		contProv2.setElements(input2.toArray(new NodeSettingsItem[input2.size()]));
		NodeSettingsItem[] leftItems = (NodeSettingsItem[]) contProv1.getElements(null);
		NodeSettingsItem[] rightItems = (NodeSettingsItem[]) contProv2.getElements(null);
		NodeSettingsTreeViewer.colorItems(leftItems, rightItems);
		refresh();
	}

	public void setElements(Object[] input1, Object[] input2) {
		((NodeSettingsContentProvider) m_treeViewerLeft.getContentProvider()).setElements(input1);
		((NodeSettingsContentProvider) m_treeViewerRight.getContentProvider()).setElements(input2);
	}

}
