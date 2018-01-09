/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
package org.knime.workbench.workflowdiff.editor;

import java.util.HashMap;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;
import org.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import org.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

/**
 * View for the settings of one node. Used in the bottom part of the compare,
 * one for the left node, one for the right node settings.
 * 
 * @author ohl
 */
public class NodeSettingsTreeViewer extends TreeViewer implements IFilterableTreeViewer {

    public static final Color DIFFERENT = new Color(Display.getDefault(), 255, 196, 196);

    public static final Color CONTAINSDIFF = Display.getDefault().getSystemColor(SWT.COLOR_GRAY);

    public static final Color DEFAULT = Display.getDefault().getSystemColor(SWT.COLOR_WHITE);

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
     * New instance of the tree.
     * 
     * @param parent
     *            of the tree.
     */
    public NodeSettingsTreeViewer(final Composite parent, int style) {
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

        setContentProvider(new NodeSettingsTreeContentProvider());
        m_labelProv = new NodeSettingsTableLabelProvider();
        setLabelProvider(m_labelProv);
        setInput("");
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

    @Override
    public String getMatchLabel(IHierMatchableItem item, int col) {
        return m_labelProv.getColumnText(item, col);
    }

    @Override
    public int getMatchNumOfColumns() {
        return getTree().getColumnCount();
    }

    /**
     * Expands the item in the tree that has the same "path" than the passed
     * item. I.e. the item could be from the "other" NodeSettingsTreeViewer and
     * if the item with the same (hierarchical) id exists in this tree it will
     * be expanded.
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
            items1.put(item.getID(), item); // column 0 contains the settings
                                            // "name" - should be the unique
                                            // id
        }
        for (SettingsItem item : itemSet2) {
            items2.put(item.getID(), item);
        }

        for (SettingsItem item : itemSet1) {
            item.setBackground(DEFAULT);
            boolean diff = false;
            SettingsItem otherItem = items2.get(item.getID());
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
                    if (!objectEquals(item.getType(), otherItem.getType())) {
                        // different type
                        item.setBackground(1, DIFFERENT);
                        diff = true;
                    }
                    if (!objectEquals(item.getValue(), otherItem.getValue())) {
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
            SettingsItem otherItem = items1.get(item.getID());
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
                    if (!objectEquals(item.getType(), otherItem.getType())) {
                        // different type
                        item.setBackground(1, DIFFERENT);
                        diff = true;
                    }
                    if (!objectEquals(item.getValue(), otherItem.getValue())) {
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
     * Sets the background color of all children (recursively) of the passed
     * tree item.
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
}
