package com.knime.workbench.workflowdiff.nodesettingsview;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

public class NodeSettingsLabelProvider implements ITableLabelProvider, ITableColorProvider {

	@Override
	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof NodeSettingsItem) {
			NodeSettingsItem settings = (NodeSettingsItem) element;
			switch (columnIndex) {
			case 0:
				return settings.getID();
			case 1:
				return settings.getType();
			case 2:
				return settings.getValue();
			default:
				break;
			}
		}
		return "";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Color getBackground(final Object element, final int columnIndex) {
		if (element instanceof NodeSettingsItem && columnIndex < 3) {
			return ((NodeSettingsItem) element).getBackground(columnIndex);
		}
		return null;
	}

	@Override
	public Color getForeground(Object element, int columnIndex) {
		return null;
	}

}
