package com.knime.workbench.workflowdiff.editor;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

/**
 * Label Provider for node settings viewer.
 * 
 * @author ohl
 */
public class NodeSettingsTableLabelProvider implements ITableLabelProvider, ITableColorProvider {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addListener(final ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLabelProperty(final Object element, final String property) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeListener(final ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Image getColumnImage(final Object element, final int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getColumnText(final Object element, final int columnIndex) {
		if (element instanceof SettingsItem) {
			return ((SettingsItem) element).getText(columnIndex);
		}
		return "";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Color getBackground(final Object element, final int columnIndex) {
		if (element instanceof SettingsItem && columnIndex < 3) {
			return ((SettingsItem) element).getBackground(columnIndex);
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Color getForeground(final Object element, final int columnIndex) {
		return null;
	}
}