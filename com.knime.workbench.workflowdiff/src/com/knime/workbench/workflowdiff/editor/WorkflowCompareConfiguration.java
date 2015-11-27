/**
 * 
 */
package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.CompareConfiguration;

/**
 * Used to store the nodes that should be compared. The workflow diff supports the diff of two totally different (not
 * matching) nodes in the workflows (i.e. nodes on different rows in the tree of nodes in the top view in the editor).
 * Therefore the default mechanism that displays the diff of the double clicked element in both workflows doesn't work.
 * The structure viewer (top view) stores the selected nodes in the configuration and the merge viewer (bottom view)
 * takes it from here and shows the diffs.
 * 
 * @author ohl
 *
 */
public class WorkflowCompareConfiguration extends CompareConfiguration {

	private FlowDiffNode m_leftSelection;
	private FlowDiffNode m_rightSelection;
	private NodeSettingsMergeViewer m_contViewer;

	public FlowDiffNode getRightSelection() {
		return m_rightSelection;
	}

	public FlowDiffNode getLeftSelection() {
		return m_leftSelection;
	}

	public void setRightSelection(FlowDiffNode rightSelection) {
		m_rightSelection = rightSelection;
	}

	public void setLeftSelection(FlowDiffNode leftSelection) {
		m_leftSelection = leftSelection;
	}

	public void setContViewer(NodeSettingsMergeViewer contViewer) {
		this.m_contViewer=contViewer;
	}
	
	public NodeSettingsMergeViewer getContViewer(){
		return m_contViewer;
	}

}
