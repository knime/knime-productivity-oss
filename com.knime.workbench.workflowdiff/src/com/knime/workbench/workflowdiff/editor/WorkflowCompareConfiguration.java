/**
 * 
 */
package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.CompareConfiguration;

import com.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput.FlowDiffNode;

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

	public FlowDiffNode getRightSelection() {
		return m_rightSelection;
	}

	public FlowDiffNode getLeftSelection() {
		return m_leftSelection;
	}

	public void setRightSelection(FlowDiffNode rightSelection) {
		m_rightSelection = rightSelection;
		if (m_leftSelection == null) {
			// the first selection also sets the second one. So if only one click occurs the nodes in that one row are
			// compared then.
			m_leftSelection = rightSelection;
		}
	}

	public void setLeftSelection(FlowDiffNode leftSelection) {
		m_leftSelection = leftSelection;
		if (m_rightSelection == null) {
			// the first selection also sets the second one. So if only one click occurs the nodes in that one row are
			// compared then.
			m_rightSelection = leftSelection;
		}
	}

}
