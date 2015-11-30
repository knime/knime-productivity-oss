package com.knime.workbench.workflowdiff.editor;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.swt.graphics.Color;

import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

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
			m_children = new ArrayList<SettingsItem>();
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
			Arrays.fill(m_background, NodeSettingsTreeViewer.DEFAULT);
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
		return m_background == null ? NodeSettingsTreeViewer.DEFAULT : m_background[column];
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
			return compareObjects(getText(0), otherItem.getText(0)) && compareObjects(getText(1), otherItem.getText(1));
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