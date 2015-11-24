package com.knime.workbench.workflowdiff.editor.filters;

import org.eclipse.jface.viewers.ViewerFilter;

public interface IFilterableTreeViewer {

	/**
	 * Show an indicator that the view is filtered.
	 * 
	 * @param showIt
	 *            or hideIt
	 */
	public void setFilterIcon(final boolean showIt);

	public void addFilter(final ViewerFilter filter);

	/**
	 */
	
	/**
	 * Return the label in the corresponding column to compare in the filter. Preferably get it from the label provider.
	 * @param item to return the label for
	 * @param col index of the column
	 * @return
	 */
	public String getMatchLabel(IHierMatchableItem item, final int col);

	/**
	 * Number of cols in the viewer.
	 * 
	 * @return
	 */
	public int getMatchNumOfColumns();

	public void refresh();

}
