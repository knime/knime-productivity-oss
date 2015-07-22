package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;

import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

/**
 * Using our own class to be able to distinguish from others and to instantiate our own viewer.
 *
 * @author ohl
 */
public class FlowDiffNode extends DiffNode implements IHierMatchableItem {

	private boolean fDirty = false;

	private ITypedElement fLastId;

	private String fLastName;

	public FlowDiffNode(final IDiffContainer parent, final int description, final ITypedElement ancestor,
			final ITypedElement left, final ITypedElement right) {
		super(parent, description, ancestor, left, right);
	}

	void clearDirty() {
		fDirty = false;
	}

	@Override
	public String getName() {
		if (fLastName == null) {
			fLastName = super.getName();
		}
		if (fDirty) {
			return '<' + fLastName + '>';
		}
		return fLastName;
	}

	@Override
	public ITypedElement getId() {
		ITypedElement id = super.getId();
		if (id == null) {
			return fLastId;
		}
		fLastId = id;
		return id;
	}

	@Override
	public IHierMatchableItem[] getMatchChildren() {
		IDiffElement[] children = getChildren();
		IHierMatchableItem[] result = new IHierMatchableItem[children.length];
		for (int i = 0; i < children.length; i++) {
			result[i] = (IHierMatchableItem) children[i];
		}
		return result;
	}

	@Override
	public IHierMatchableItem getMatchParent() {
		IDiffElement p = getParent();
		if (p instanceof IHierMatchableItem) {
			return (IHierMatchableItem) p;
		}
		return null;
	}

}