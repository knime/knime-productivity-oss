package com.knime.workbench.workflowdiff.editor.filters;

import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.jface.viewers.Viewer;

import com.knime.workbench.workflowdiff.editor.FlowDiffNode;

public class StructureDiffFilter extends NodeDiffFilter {

	private boolean m_hideEqualNodes;
	private boolean m_showAdditionsOnly;

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (element instanceof FlowDiffNode) {
			if (m_hideEqualNodes) {
				return !isAndContainsPseudosOnly((FlowDiffNode) element);
			}
			if (m_showAdditionsOnly) {
				if (!isOrContainsAdditions((FlowDiffNode) element)) {
					return false;
				}
			}
		}
		return super.select(viewer, parentElement, element);
	}

	private boolean isAndContainsPseudosOnly(final FlowDiffNode n) {
		if (n.getKind() != Differencer.PSEUDO_CONFLICT) {
			return false;
		}
		IDiffElement[] children = n.getChildren();
		if (children != null) {
			for (IDiffElement c : children) {
				if (!(c instanceof FlowDiffNode)) {
					return false;
				}
				if (!isAndContainsPseudosOnly((FlowDiffNode) c)) {
					return false;
				}
			}
		}
		return true;
	}
	
	private boolean isOrContainsAdditions(final FlowDiffNode n) {
		int kind = n.getKind();
		if (kind == Differencer.ADDITION || kind == Differencer.DELETION) {
			return true;
		}
		IDiffElement[] children = n.getChildren();
		if (children != null) {
			for (IDiffElement c : children) {
				if ((c instanceof FlowDiffNode) && isOrContainsAdditions((FlowDiffNode) c)) {
					return true;
				}
			}
		}
		return false;

		
	}
	public void setHideEqualNodes(boolean hideEqualNodes) {
		m_hideEqualNodes = hideEqualNodes;
	}

	public void setShowAdditionsOnly(boolean showAdditionsOnly) {
		m_showAdditionsOnly = showAdditionsOnly;
	}
}
