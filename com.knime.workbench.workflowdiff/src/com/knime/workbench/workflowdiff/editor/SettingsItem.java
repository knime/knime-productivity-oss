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

    private String m_id;
    private String m_type;
    private String m_value;

    private Color[] m_background = null;

    /**
     * Tree item in the root level.
     */
    public SettingsItem(String id, String type, String value) {
        m_id = id;
        m_type = type;
        m_value = value;
        m_parent = null;
    }

    /**
     * Tree item down in the hierarchy.
     * 
     * @param parent
     *            must not be null
     */
    public SettingsItem(final String id, final String type, final String value, final SettingsItem parent) {
        m_id = id;
        m_type = type;
        m_value = value;
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

    public String getID() {
        return m_id;
    }

    public String getType() {
        return m_type;
    }

    public String getValue() {
        return m_value;
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
            return compareObjects(getID(), otherItem.getID()) && compareObjects(getType(), otherItem.getType());
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
        return (getID() == null ? 0 : getID().hashCode()) ^ (getType() == null ? 0 : getType().hashCode());
    }
}