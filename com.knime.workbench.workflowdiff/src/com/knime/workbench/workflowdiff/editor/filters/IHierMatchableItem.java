package com.knime.workbench.workflowdiff.editor.filters;

/**
 *
 */
public interface IHierMatchableItem {

	public IHierMatchableItem getMatchParent();
	
	public IHierMatchableItem[] getMatchChildren();
}
