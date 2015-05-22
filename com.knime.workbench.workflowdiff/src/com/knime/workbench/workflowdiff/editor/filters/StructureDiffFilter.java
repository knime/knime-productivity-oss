package com.knime.workbench.workflowdiff.editor.filters;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.jface.viewers.Viewer;

import com.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput;

public class StructureDiffFilter extends NodeDiffFilter {

	private boolean m_hideEqualNodes;
	private boolean m_showAdditionsOnly;

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (element instanceof WorkflowCompareEditorInput.FlowDiffNode) {
			if (m_hideEqualNodes) {
				if (((WorkflowCompareEditorInput.FlowDiffNode) element).getKind() == Differencer.PSEUDO_CONFLICT) {
					return false;
				}
			}
			if (m_showAdditionsOnly) {
				if ((((WorkflowCompareEditorInput.FlowDiffNode) element).getKind() != Differencer.ADDITION)
						&& (((WorkflowCompareEditorInput.FlowDiffNode) element).getKind() != Differencer.DELETION)) {
					return false;
				}
			}
		}
		return super.select(viewer, parentElement, element);
	}

	public void setHideEqualNodes(boolean hideEqualNodes) {
		m_hideEqualNodes = hideEqualNodes;
	}

	public void setShowAdditionsOnly(boolean showAdditionsOnly) {
		m_showAdditionsOnly = showAdditionsOnly;
	}
}
