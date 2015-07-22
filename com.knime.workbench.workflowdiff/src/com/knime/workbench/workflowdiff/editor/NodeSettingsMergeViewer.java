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
 *   20.10.2014 (ohl): created
 */
package com.knime.workbench.workflowdiff.editor;

import java.util.ResourceBundle;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.contentmergeviewer.ContentMergeViewer;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.swt.widgets.Composite;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;
import com.knime.workbench.workflowdiff.editor.NodeSettingsTreeViewer.NodeSettingsTreeContentProvider;
import com.knime.workbench.workflowdiff.editor.NodeSettingsTreeViewer.SettingsItem;
import com.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilter;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffClearFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilterContribution;

/**
 * Bottom view of the compare editor. Shows two settings trees.
 * @author ohl
 */
public class NodeSettingsMergeViewer extends ContentMergeViewer {

    private NodeSettingsTreeViewer m_left;

    private NodeSettingsTreeViewer m_right;

    private NodeSettingsTreeViewer m_ancestor;

    /**
     * @param parent the parent
     * @param styles SWT style(s)
     * @param mp configuration for the viewer
     */
    protected NodeSettingsMergeViewer(final Composite parent, final int styles, final CompareConfiguration mp) {
        super(styles, ResourceBundle.getBundle("org.eclipse.compare.contentmergeviewer.TextMergeViewerResources"), mp);
        buildControl(parent);
        getControl().setData(CompareUI.COMPARE_VIEWER_TITLE, "Node Settings Comparison");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createControls(final Composite composite) {
        m_ancestor = new NodeSettingsTreeViewer(composite);
        m_left = new NodeSettingsTreeViewer(composite);
        m_right = new NodeSettingsTreeViewer(composite);

        /* Let the trees expand/collapse synchronously.
         * It is important that the setExpanded method call doesn't
         * trigger a listener notification. Obviously. */
        m_left.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem)event.getElement();
                m_right.setExpanded(sItem, true);
            }

            @Override
            public void treeCollapsed(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem)event.getElement();
                m_right.setExpanded(sItem, false);
            }
        });
        m_right.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem)event.getElement();
                m_left.setExpanded(sItem, true);
            }

            @Override
            public void treeCollapsed(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem)event.getElement();
                m_left.setExpanded(sItem, false);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createToolItems(final ToolBarManager toolBarManager) {
        // TODO Auto-generated method stub
        super.createToolItems(toolBarManager);
        NodeDiffFilterContribution searchTextField =
            new NodeDiffFilterContribution(new NodeDiffFilter(), m_left, m_right);
        toolBarManager.add(searchTextField);
        toolBarManager.add(new NodeDiffClearFilterButton(searchTextField));
    }

    @Override
    protected void handleResizeAncestor(final int x, final int y, final int width, final int height) {
        if (width > 0) {
            m_ancestor.setVisible(true);
            m_ancestor.setBounds(x, y, width, height);
        } else {
            m_ancestor.setVisible(false);
        }
    }

    @Override
    protected void handleResizeLeftRight(final int x, final int y, final int width1, final int centerWidth,
        final int width2, final int height) {
        m_left.setBounds(x, y, width1, height);
        m_right.setBounds(x + width1 + centerWidth, y, width2, height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateContent(final Object ancestor, final Object left, final Object right) {
    	WorkflowCompareConfiguration compareConfig = (WorkflowCompareConfiguration)getCompareConfiguration();
		FlowDiffNode leftSelection = compareConfig.getLeftSelection();
    	FlowDiffNode rightSelection = compareConfig.getRightSelection();
        
    	Object newLeft = null;
    	String nameLeft = "No Node Selected.";
    	if ((left != null || right != null) && (leftSelection != null) && (leftSelection.getLeft() instanceof FlowNode)) {
    		// if both are null: clear the object. Accept only nodes (no meta nodes) for comparison
    		newLeft = leftSelection.getLeft();
    		nameLeft = ((FlowNode)newLeft).getName();
    	}
    	Object newRight = null;
    	String nameRight = "No Node Selected.";
    	if ((left != null || right != null) && (rightSelection != null) && (rightSelection.getRight() instanceof FlowNode)) {
    		// if both are null: clear the object. Accept only nodes (no meta nodes) for comparison
    		newRight = rightSelection.getRight();
    		nameRight = ((FlowNode)newRight).getName();
    	}
    	m_ancestor.setInput(ancestor);
    	m_left.setInput(newLeft);
    	m_right.setInput(newRight);
    	
    	compareConfig.setLeftLabel(nameLeft);
    	compareConfig.setRightLabel(nameRight);
        SettingsItem[] leftItems =
                (SettingsItem[])((NodeSettingsTreeContentProvider)m_left.getContentProvider()).getElements(null);
        SettingsItem[] rightItems =
                (SettingsItem[])((NodeSettingsTreeContentProvider)m_right.getContentProvider()).getElements(null);
        if (leftItems != null && rightItems != null && leftItems.length > 0 && rightItems.length > 0) {
            NodeSettingsTreeViewer.colorItems(leftItems, rightItems);
            m_left.refresh();
            m_right.refresh();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void copy(final boolean leftToRight) {
        // can't modify content
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected byte[] getContents(final boolean left) {
        //        if (left && (m_left.getInput() instanceof FlowNode)) {
        //            return ((FlowNode)m_left.getInput()).getBytes();
        //        }
        //        if (!left && (m_right.getInput() instanceof FlowNode)) {
        //            return ((FlowNode)m_right.getInput()).getBytes();
        //        }
        return new byte[]{};
    }

}
