package com.knime.workbench.workflowdiff.nodesettingsview;

import java.util.HashMap;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

import com.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

public class NodeSettingsTreeViewer extends TreeViewer implements IFilterableTreeViewer {

	public NodeSettingsTreeViewer(Composite parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	public NodeSettingsTreeViewer(Composite parent, int style) {
		super(parent, style);
		Tree tree = getTree();
		tree.setHeaderVisible(true);
		TreeColumn key = new TreeColumn(tree, SWT.LEFT);
		key.setText("Name");
		key.setWidth(200);
		TreeColumn type = new TreeColumn(tree, SWT.LEFT);
		type.setText("Type");
		type.setWidth(200);
		TreeColumn value = new TreeColumn(tree, SWT.LEFT);
		value.setText("Value");
		value.setWidth(200);
		setContentProvider(new NodeSettingsContentProvider());
		setLabelProvider(new NodeSettingsLabelProvider());
		setInput("");
	}

	@Override
	public String getMatchLabel(IHierMatchableItem item, int col) {
		return ((NodeSettingsLabelProvider) getLabelProvider()).getColumnText(item, col);
	}

	@Override
	public int getMatchNumOfColumns() {
		return getTree().getColumnCount();
	}

	/**
	 * Shows a funnel icon in the column headers.
	 * 
	 * @param showIt
	 *            or hideIt
	 */
	public void setFilterIcon(final boolean showIt) {
		Image searchImg = showIt ? ImageRepository.getIconImage(SharedImages.FunnelIcon) : null;
		for (TreeColumn col : getTree().getColumns()) {
			col.setImage(searchImg);
		}
	}

	public void setVisible(final boolean visible) {
		getTree().setVisible(visible);
	}

	/**
	 * @param x
	 *            new x
	 * @param y
	 *            new y
	 * @param width
	 *            new width
	 * @param height
	 *            new height
	 * @see org.eclipse.swt.widgets.Control#setBounds(int, int, int, int)
	 */
	public void setBounds(int x, int y, int width, int height) {
		getTree().setBounds(x, y, width, height);
	}

	/**
	 * Sets the background color of all children (recursively) of the passed
	 * tree item.
	 * 
	 * @param parent
	 *            of the items that should be colored
	 * @param col
	 *            background color to set
	 */
	private static void colorDownTree(final NodeSettingsItem parent, final Color col) {
		for (NodeSettingsItem child : parent.getChildren()) {
			child.setBackground(col);
			colorDownTree(child, col);
		}
	}

	private static void colorUpTree(final NodeSettingsItem child, final Color col) {
		NodeSettingsItem parent = child.getParent();
		if (parent == null) {
			return;
		}
		parent.setBackground(col);
		colorUpTree(parent, col);
	}

	public static void colorItems(final NodeSettingsItem[] itemSet1, final NodeSettingsItem[] itemSet2) {
		HashMap<String, NodeSettingsItem> items1 = new HashMap<String, NodeSettingsItem>(itemSet1.length);
		HashMap<String, NodeSettingsItem> items2 = new HashMap<String, NodeSettingsItem>(itemSet2.length);
		for (NodeSettingsItem item : itemSet1) {
			items1.put(item.getID(), item); // column 0 contains the settings
											// "name" - should be the unique
											// id
		}
		for (NodeSettingsItem item : itemSet2) {
			items2.put(item.getID(), item);
		}

		for (NodeSettingsItem item : itemSet1) {
			item.setBackground(NodeSettingsItem.DEFAULT);
			boolean diff = false;
			NodeSettingsItem otherItem = items2.get(item.getID());
			if (item.getChildren().length > 0) {
				if (otherItem == null) {
					// new subconfig
					item.setBackground(NodeSettingsItem.DIFFERENT);
					colorDownTree(item, NodeSettingsItem.DIFFERENT);
					colorUpTree(item, NodeSettingsItem.CONTAINSDIFF);
				} else {
					colorItems(item.getChildren(), otherItem.getChildren());
				}
			} else {
				if (otherItem == null) {
					item.setBackground(NodeSettingsItem.DIFFERENT);
					diff = true;
				} else {
					// also in the other tree
					if (!objectEquals(item.getType(), otherItem.getType())) {
						// different type
						item.setBackground(1, NodeSettingsItem.DIFFERENT);
						diff = true;
					}
					if (!objectEquals(item.getValue(), otherItem.getValue())) {
						// different value
						item.setBackground(2, NodeSettingsItem.DIFFERENT);
						diff = true;
					}
				}
				if (diff) {
					colorUpTree(item, NodeSettingsItem.CONTAINSDIFF);
				}
			}
		}
		for (NodeSettingsItem item : itemSet2) {
			item.setBackground(NodeSettingsItem.DEFAULT);
			boolean diff = false;
			NodeSettingsItem otherItem = items1.get(item.getID());
			if (item.getChildren().length > 0) {
				if (otherItem == null) {
					// new subconfig
					item.setBackground(NodeSettingsItem.DIFFERENT);
					colorDownTree(item, NodeSettingsItem.DIFFERENT);
					colorUpTree(item, NodeSettingsItem.CONTAINSDIFF);
				} else {
					colorItems(item.getChildren(), otherItem.getChildren());
				}
			} else {
				if (otherItem == null) {
					item.setBackground(NodeSettingsItem.DIFFERENT);
					diff = true;
				} else {
					// also in the other tree
					if (!objectEquals(item.getType(), otherItem.getType())) {
						// different type
						item.setBackground(1, NodeSettingsItem.DIFFERENT);
						diff = true;
					}
					if (!objectEquals(item.getValue(), otherItem.getValue())) {
						// different value
						item.setBackground(2, NodeSettingsItem.DIFFERENT);
						diff = true;
					}
				}
				if (diff) {
					colorUpTree(item, NodeSettingsItem.CONTAINSDIFF);
				}
			}
		}

	}

	private static boolean objectEquals(final Object o1, final Object o2) {
		return o1 == null ? o2 == null : o1.equals(o2);
	}

}
