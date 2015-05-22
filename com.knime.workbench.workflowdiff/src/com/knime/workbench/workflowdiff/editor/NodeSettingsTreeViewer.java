/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   21.10.2014 (ohl): created
 */
package com.knime.workbench.workflowdiff.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;

import javax.swing.tree.TreeNode;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.config.base.AbstractConfigEntry;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;
import com.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilter;

/**
 * View for the settings of one node. Used in the bottom part of the compare, one for the left node, one for the right
 * node settings.
 * 
 * @author ohl
 */
public class NodeSettingsTreeViewer extends TreeViewer implements IFilterableTreeViewer {

	private final NodeSettingsTableLabelProvider m_labelProv;
	
	/**
	 * New instance of the tree.
	 * 
	 * @param parent
	 *            of the tree.
	 */
	public NodeSettingsTreeViewer(final Composite parent) {
		super(parent, SWT.NO_FOCUS | SWT.BORDER);
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

		setContentProvider(new NodeSettingsTreeContentProvider());
		m_labelProv = new NodeSettingsTableLabelProvider();
		setLabelProvider(m_labelProv);
	}

	/**
	 * Shows a funnel icon in the column headers.
	 * 
	 * @param showIt
	 *            or hideIt
	 */
	public void setFilterIcon(final boolean showIt) {
		Image searchImg = showIt ? ImageRepository.getImage(SharedImages.FunnelIcon) : null;
		for (TreeColumn col : getTree().getColumns()) {
			col.setImage(searchImg);
		}
	}

	@Override
	public String getMatchLabel(IHierMatchableItem item, int col) {
		return m_labelProv.getColumnText(item, col);
	}
	
	@Override
	public int getMatchNumOfColumns() {
		return getTree().getColumnCount();
	}
	
	/**
	 * Expands the item in the tree that has the same "path" than the passed item. I.e. the item could be from the
	 * "other" NodeSettingsTreeViewer and if the item with the same (hierarchical) id exists in this tree it will be
	 * expanded.
	 * 
	 * @param item
	 *            to find in this tree and to set the expand state
	 * @param expand
	 *            or not.
	 */
	public void setExpanded(final SettingsItem item, final boolean expand) {
		if (expand) {
			expandToLevel(item, 1); // uses equals to find the item
		} else {
			collapseToLevel(item, 1);
		}
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
	public void setBounds(final int x, final int y, final int width, final int height) {
		getTree().setBounds(x, y, width, height);
	}

	/**
	 * @param rect
	 *            to set as new bounds
	 * @see org.eclipse.swt.widgets.Control#setBounds(org.eclipse.swt.graphics.Rectangle)
	 */
	public void setBounds(final Rectangle rect) {
		getTree().setBounds(rect);
	}

	public void setVisible(final boolean visible) {
		getTree().setVisible(visible);
	}

	public static void colorItems(final SettingsItem[] itemSet1, final SettingsItem[] itemSet2) {
		HashMap<String, SettingsItem> items1 = new HashMap<String, SettingsItem>(itemSet1.length);
		HashMap<String, SettingsItem> items2 = new HashMap<String, SettingsItem>(itemSet2.length);
		for (SettingsItem item : itemSet1) {
			items1.put(item.getText(0), item); // column 0 contains the settings
												// "name" - should be the unique
												// id
		}
		for (SettingsItem item : itemSet2) {
			items2.put(item.getText(0), item);
		}

		for (SettingsItem item : itemSet1) {
			item.setBackground(DEFAULT);
			boolean diff = false;
			SettingsItem otherItem = items2.get(item.getText(0));
			if (item.getChildren().length > 0) {
				if (otherItem == null) {
					// new subconfig
					item.setBackground(DIFFERENT);
					colorDownTree(item, DIFFERENT);
					colorUpTree(item, CONTAINSDIFF);
				} else {
					colorItems(item.getChildren(), otherItem.getChildren());
				}
			} else {
				if (otherItem == null) {
					item.setBackground(DIFFERENT);
					diff = true;
				} else {
					// also in the other tree
					if (!objectEquals(item.getText(1), otherItem.getText(1))) {
						// different type
						item.setBackground(1, DIFFERENT);
						diff = true;
					}
					if (!objectEquals(item.getText(2), otherItem.getText(2))) {
						// different value
						item.setBackground(2, DIFFERENT);
						diff = true;
					}
				}
				if (diff) {
					colorUpTree(item, CONTAINSDIFF);
				}
			}
		}
		for (SettingsItem item : itemSet2) {
			item.setBackground(DEFAULT);
			boolean diff = false;
			SettingsItem otherItem = items1.get(item.getText(0));
			if (item.getChildren().length > 0) {
				if (otherItem == null) {
					// new subconfig
					item.setBackground(DIFFERENT);
					colorDownTree(item, DIFFERENT);
					colorUpTree(item, CONTAINSDIFF);
				} else {
					colorItems(item.getChildren(), otherItem.getChildren());
				}
			} else {
				if (otherItem == null) {
					item.setBackground(DIFFERENT);
					diff = true;
				} else {
					// also in the other tree
					if (!objectEquals(item.getText(1), otherItem.getText(1))) {
						// different type
						item.setBackground(1, DIFFERENT);
						diff = true;
					}
					if (!objectEquals(item.getText(2), otherItem.getText(2))) {
						// different value
						item.setBackground(2, DIFFERENT);
						diff = true;
					}
				}
				if (diff) {
					colorUpTree(item, CONTAINSDIFF);
				}
			}
		}

	}

	private static boolean objectEquals(final Object o1, final Object o2) {
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	/**
	 * Sets the background color of all children (recursively) of the passed tree item.
	 * 
	 * @param parent
	 *            of the items that should be colored
	 * @param col
	 *            background color to set
	 */
	private static void colorDownTree(final SettingsItem parent, final Color col) {
		for (SettingsItem child : parent.getChildren()) {
			child.setBackground(col);
			colorDownTree(child, col);
		}
	}

	private static void colorUpTree(final SettingsItem child, final Color col) {
		SettingsItem parent = child.getParent();
		if (parent == null) {
			return;
		}
		parent.setBackground(col);
		colorUpTree(parent, col);
	}

	public static final Color DIFFERENT = new Color(Display.getDefault(), 255, 196, 196);

	public static final Color CONTAINSDIFF = Display.getDefault().getSystemColor(SWT.COLOR_GRAY);

	public static final Color DEFAULT = Display.getDefault().getSystemColor(SWT.COLOR_WHITE);

	/**
	 * Item in the tree of the viewer.
	 * 
	 * @author ohl
	 */
	public class SettingsItem implements IHierMatchableItem {

		private final SettingsItem m_parent;

		private ArrayList<SettingsItem> m_children;

		private String[] m_text = new String[0];

		private Color[] m_background = null;

		/**
		 * Tree item in the root level.
		 */
		SettingsItem() {
			m_parent = null;
		}

		/**
		 * Tree item down in the hierarchy.
		 * 
		 * @param parent
		 *            must not be null
		 */
		SettingsItem(final SettingsItem parent) {
			m_parent = parent;
			m_parent.addChild(this);
		}

		private void addChild(final SettingsItem child) {
			if (m_children == null) {
				m_children = new ArrayList<NodeSettingsTreeViewer.SettingsItem>();
			}
			m_children.add(child);
		}

		/**
		 * @return a (possibly empty) array of children. Never null.
		 */
		public SettingsItem[] getChildren() {
			if (m_children == null) {
				return new SettingsItem[0];
			}
			return m_children.toArray(new SettingsItem[m_children.size()]);
		}

		@Override
		public IHierMatchableItem[] getMatchChildren() {
			return getChildren();
		}
		/**
		 * @return true if this item has children
		 */
		public boolean hasChildren() {
			return m_children == null ? false : m_children.size() > 0;
		}

		/**
		 * @return the parent. Or null if this is at the root level.
		 */
		public SettingsItem getParent() {
			return m_parent;
		}

		@Override
		public IHierMatchableItem getMatchParent() {
			return getParent();
		}
		
		/**
		 * Set the content of the item.
		 * 
		 * @param strings
		 *            for all columns
		 */
		public void setText(final String... strings) {
			m_text = Arrays.copyOf(strings, strings.length);
		}

		/**
		 * @param index
		 *            of the column to retrieve the text from
		 * @return the text in the specified column, or null if index too large
		 */
		public String getText(final int index) {
			if (index >= m_text.length) {
				return null;
			}
			return m_text[index];
		}

		/**
		 * Sets color in all columns.
		 * 
		 * @param clr
		 *            to set
		 */
		public void setBackground(final Color clr) {
			if (m_background == null) {
				m_background = new Color[3];
			}
			Arrays.fill(m_background, clr);
		}

		public void setBackground(final int column, final Color clr) {
			if (m_background == null) {
				m_background = new Color[3];
				Arrays.fill(m_background, DEFAULT);
			}
			m_background[column] = clr;
		}

		/**
		 * Returns the color for the specified column.
		 * 
		 * @param column
		 *            to return the color for
		 * @return color for specified column
		 */
		public Color getBackground(final int column) {
			return m_background == null ? DEFAULT : m_background[column];
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(final Object obj) {
			if (obj instanceof SettingsItem) {
				SettingsItem otherItem = (SettingsItem) obj;
				// make sure we are in the same hierarchy path
				if (!compareObjects(getParent(), otherItem.getParent())) {
					return false;
				}
				return compareObjects(getText(0), otherItem.getText(0))
						&& compareObjects(getText(1), otherItem.getText(1));
			}
			return false;
		}

		/**
		 * Compares two objects.
		 * 
		 * @param s1
		 *            the object or null
		 * @param s2
		 *            the other object or null
		 * @return true or false.
		 */
		private boolean compareObjects(final Object s1, final Object s2) {
			return s1 == null ? s2 == null : s1.equals(s2);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return (getText(0) == null ? 0 : getText(0).hashCode()) ^ (getText(1) == null ? 0 : getText(1).hashCode());
		}
	}

	/**
	 * Content provider for node settings tree viewer.
	 * 
	 * @author ohl
	 */
	public class NodeSettingsTreeContentProvider implements ITreeContentProvider {

		private SettingsItem[] m_settings;

		@Override
		public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {

			if (newInput instanceof FlowNode) {

				NodeContainer nc = ((FlowNode) newInput).getNode();
				WorkflowManager wfm = nc.getParent();
				Config modelSettings;
				Config memorySettings = null;
				Config jobMgrSettings = null;
				NodeSettings nodeSettings = new NodeSettings("Props");
				try {
					wfm.saveNodeSettings(nc.getID(), nodeSettings);
					modelSettings = nodeSettings.getNodeSettings("model");
					if (nodeSettings.containsKey(Node.CFG_MISC_SETTINGS)) {
						memorySettings = nodeSettings.getNodeSettings(Node.CFG_MISC_SETTINGS);
					}
					if (nodeSettings.containsKey("job.manager")) {
						jobMgrSettings = nodeSettings.getNodeSettings("job.manager");
					}
				} catch (InvalidSettingsException e) {
					SettingsItem errItem = new SettingsItem();
					errItem.setText("ERROR", "No settings available", e.getMessage());
					m_settings = new SettingsItem[] { errItem };
					return;
				}

				ArrayList<SettingsItem> input = new ArrayList<NodeSettingsTreeViewer.SettingsItem>(3);
				SettingsItem modelItem = new SettingsItem();
				modelItem.setText("Node Settings");
				addAllConfigValues(modelItem, modelSettings);
				input.add(modelItem);
				if (memorySettings != null) {
					SettingsItem memoryItem = new SettingsItem();
					memoryItem.setText("System Node Settings");
					addAllConfigValues(memoryItem, memorySettings);
					input.add(memoryItem);
				}
				if (jobMgrSettings != null) {
					SettingsItem jobMgrItem = new SettingsItem();
					jobMgrItem.setText("Job Manager Settings");
					addAllConfigValues(jobMgrItem, jobMgrSettings);
					input.add(jobMgrItem);
				}
				m_settings = input.toArray(new SettingsItem[input.size()]);
			} else {
				m_settings = new SettingsItem[0];
			}

		}

		/**
		 * Recursively adds all settings from the config as children to the tree item.
		 * 
		 * @param item
		 * @param config
		 */
		private void addAllConfigValues(final SettingsItem item, final Config config) {
			for (Enumeration<TreeNode> it = config.children(); it.hasMoreElements();) {
				AbstractConfigEntry prop = (AbstractConfigEntry) it.nextElement();
				if (prop instanceof Config) {
					// sub-config
					SettingsItem subConfig = new SettingsItem(item);
					subConfig.setText(prop.getKey(), "sub-config");
					addAllConfigValues(subConfig, (Config) prop);
				} else {
					// all settings are displayed as string
					String id = prop.getKey();
					String type = prop.getType().name().substring(1);
					String value = prop.toStringValue();

					SettingsItem settingsItem = new SettingsItem(item);
					settingsItem.setText(new String[] { id, type, value });
				}
			}
		}

		@Override
		public void dispose() {
			m_settings = null;
		}

		@Override
		public boolean hasChildren(final Object element) {
			if (element instanceof SettingsItem) {
				return ((SettingsItem) element).hasChildren();
			}
			return false;
		}

		@Override
		public Object getParent(final Object element) {
			if (element instanceof SettingsItem) {
				return ((SettingsItem) element).getParent();
			}
			return null;
		}

		@Override
		public Object[] getElements(final Object inputElement) {
			return m_settings;
		}

		@Override
		public Object[] getChildren(final Object parentElement) {
			if (parentElement instanceof SettingsItem) {
				return ((SettingsItem) parentElement).getChildren();
			}
			return null;
		}
	}

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
}
