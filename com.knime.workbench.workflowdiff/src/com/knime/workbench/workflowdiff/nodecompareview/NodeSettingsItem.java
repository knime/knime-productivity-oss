package com.knime.workbench.workflowdiff.nodecompareview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

public class NodeSettingsItem implements IHierMatchableItem, Comparable<NodeSettingsItem> {
	private String m_id;
	private String m_type;
	private String m_value;

	private NodeSettingsItem m_parent;

	private ArrayList<NodeSettingsItem> m_children;

	private Color[] m_background = null;

	public static final Color DIFFERENT = new Color(Display.getDefault(), 255, 196, 196);

	public static final Color CONTAINSDIFF = Display.getDefault().getSystemColor(SWT.COLOR_GRAY);

	public static final Color DEFAULT = Display.getDefault().getSystemColor(SWT.COLOR_WHITE);

	public NodeSettingsItem(String id, String type, String value) {
		m_id = id;
		m_type = type;
		m_value = value;
	}

	public NodeSettingsItem(String id, String type, String value, NodeSettingsItem parent) {
		m_id = id;
		m_type = type;
		m_value = value;
		m_parent = parent;
		parent.addChild(this);
	}

	public void addChild(final NodeSettingsItem child) {
		if (m_children == null) {
			m_children = new ArrayList<NodeSettingsItem>();
		}
		m_children.add(child);
		child.setParent(this);
		Collections.sort(m_children);
	}

	public String getID() {
		return m_id;
	}

	public String getType() {
		return m_type;
	}

	public String getValue() {
		return m_value;
	}

	public void setParent(NodeSettingsItem parent) {
		m_parent = parent;
	}

	public boolean hasChildren() {
		return m_children == null ? false : m_children.size() > 0;
	}

	public NodeSettingsItem getParent() {
		return m_parent;
	}

	@Override
	public IHierMatchableItem getMatchParent() {
		return getParent();
	}

	public NodeSettingsItem[] getChildren() {
		if (m_children == null) {
			return new NodeSettingsItem[0];
		}
		return m_children.toArray(new NodeSettingsItem[m_children.size()]);
	}

	@Override
	public IHierMatchableItem[] getMatchChildren() {
		return getChildren();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof NodeSettingsItem) {
			NodeSettingsItem that = (NodeSettingsItem) obj;
			if (!compareObjects(this.getParent(), that.getParent())) {
				return false;
			}
			return this.getID().equals(that.getID()) && this.getType().equals(that.getType());
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return (getID() == null ? 0 : getID().hashCode()) ^ (getType() == null ? 0 : getType().hashCode());
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

	public Color getBackground(int columnIndex) {
		return m_background == null ? DEFAULT : m_background[columnIndex];
	}

	@Override
	public int compareTo(NodeSettingsItem that) {
		return getID().compareTo(that.getID());
	}
}
