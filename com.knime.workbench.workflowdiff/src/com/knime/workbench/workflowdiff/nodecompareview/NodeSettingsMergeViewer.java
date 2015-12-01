package com.knime.workbench.workflowdiff.nodecompareview;

import java.util.ArrayList;
import java.util.ResourceBundle;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.contentmergeviewer.ContentMergeViewer;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.WorkflowManager;

import com.knime.workbench.workflowdiff.editor.NodeSettingsTreeContentProvider;
import com.knime.workbench.workflowdiff.editor.NodeSettingsTreeViewer;
import com.knime.workbench.workflowdiff.editor.SettingsItem;
import com.knime.workbench.workflowdiff.editor.WorkflowCompareConfiguration;

public class NodeSettingsMergeViewer extends ContentMergeViewer {

    private NodeSettingsTreeViewer m_left;

    private NodeSettingsTreeViewer m_right;

    private NodeSettingsTreeViewer m_ancestor;

    protected NodeSettingsMergeViewer(final int style, final ResourceBundle bundle, final CompareConfiguration cc) {
        super(style, bundle, cc);
        // TODO Auto-generated constructor stub
    }

    protected NodeSettingsMergeViewer(final Composite parent, final int style) {
        super(style, ResourceBundle.getBundle("org.eclipse.compare.contentmergeviewer.TextMergeViewerResources"),
                new WorkflowCompareConfiguration());
        buildControl(parent);
        getCompareConfiguration().setRightEditable(false);
        getCompareConfiguration().setLeftEditable(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void createControls(final Composite composite) {
        m_ancestor = new NodeSettingsTreeViewer(composite, SWT.NONE);
        m_left = new NodeSettingsTreeViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
        m_right = new NodeSettingsTreeViewer(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);

        /*
         * Let the trees expand/collapse synchronously. It is important that the
         * setExpanded method call doesn't trigger a listener notification.
         * Obviously.
         */
        m_left.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem) event.getElement();
                m_right.expandToLevel(sItem, 1);
            }

            @Override
            public void treeCollapsed(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem) event.getElement();
                m_right.collapseToLevel(sItem, 1);
            }
        });
        m_right.addTreeListener(new ITreeViewerListener() {
            @Override
            public void treeExpanded(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem) event.getElement();
                m_left.expandToLevel(sItem, 1);
            }

            @Override
            public void treeCollapsed(final TreeExpansionEvent event) {
                SettingsItem sItem = (SettingsItem) event.getElement();
                m_left.collapseToLevel(sItem, 1);
            }
        });
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
        m_left.refresh();
        m_right.refresh();
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
        // TODO Auto-generated method stub
        return null;
    }

    protected NodeSettingsTreeViewer getTreeViewer1() {
        return m_left;
    }

    protected NodeSettingsTreeViewer getTreeViewer2() {
        return m_right;
    }

    public Control getControl() {
        return m_left.getControl();
    }

    public void setInput(Object input) {
        m_left.setInput(input);
    }

    public void setElements(NodeContainer nc1, NodeContainer nc2) {
        WorkflowCompareConfiguration compareConfig = (WorkflowCompareConfiguration) getCompareConfiguration();
        compareConfig.setLeftLabel(nc1.getNameWithID());
        compareConfig.setRightLabel(nc2.getNameWithID());
        WorkflowManager wfm1 = nc1.getParent();
        WorkflowManager wfm2 = nc2.getParent();
        Config modelSettings1 = null;
        Config modelSettings2 = null;
        Config memorySettings1 = null;
        Config memorySettings2 = null;
        Config jobMgrSettings1 = null;
        Config jobMgrSettings2 = null;
        NodeSettings nodeSettings1 = new NodeSettings("Props");
        NodeSettings nodeSettings2 = new NodeSettings("Props");
        NodeSettingsTreeContentProvider contProv1 = (NodeSettingsTreeContentProvider) m_left.getContentProvider();
        NodeSettingsTreeContentProvider contProv2 = (NodeSettingsTreeContentProvider) m_right.getContentProvider();
        try {
            wfm1.saveNodeSettings(nc1.getID(), nodeSettings1);
            wfm2.saveNodeSettings(nc2.getID(), nodeSettings2);
            if (nodeSettings1.containsKey("model")) {
                modelSettings1 = nodeSettings1.getNodeSettings("model");
            }
            if (nodeSettings2.containsKey("model")) {
                modelSettings2 = nodeSettings2.getNodeSettings("model");
            }
            if (nodeSettings1.containsKey(Node.CFG_MISC_SETTINGS)) {
                memorySettings1 = nodeSettings1.getNodeSettings(Node.CFG_MISC_SETTINGS);
            }
            if (nodeSettings2.containsKey(Node.CFG_MISC_SETTINGS)) {
                memorySettings2 = nodeSettings2.getNodeSettings(Node.CFG_MISC_SETTINGS);
            }
            if (nodeSettings1.containsKey("job.manager")) {
                jobMgrSettings1 = nodeSettings1.getNodeSettings("job.manager");
            }
            if (nodeSettings2.containsKey("job.manager")) {
                jobMgrSettings2 = nodeSettings2.getNodeSettings("job.manager");
            }
        } catch (InvalidSettingsException e) {
            return;
        }
        ArrayList<SettingsItem> input1 = new ArrayList<SettingsItem>(3);
        ArrayList<SettingsItem> input2 = new ArrayList<SettingsItem>(3);
        if (modelSettings1 != null) {
            SettingsItem modelItem1 = new SettingsItem("Node Settings", "", "");
            contProv1.addAllConfigValues(modelItem1, modelSettings1);
            input1.add(modelItem1);
        }
        if (modelSettings2 != null) {
            SettingsItem modelItem2 = new SettingsItem("Node Settings", "", "");
            contProv2.addAllConfigValues(modelItem2, modelSettings2);
            input2.add(modelItem2);
        }
        if (memorySettings1 != null) {
            SettingsItem memoryItem = new SettingsItem("System Node Settings", "", "");
            contProv1.addAllConfigValues(memoryItem, memorySettings1);
            input1.add(memoryItem);
        }
        if (memorySettings2 != null) {
            SettingsItem memoryItem = new SettingsItem("System Node Settings", "", "");
            contProv2.addAllConfigValues(memoryItem, memorySettings2);
            input2.add(memoryItem);
        }
        if (jobMgrSettings1 != null) {
            SettingsItem jobMgrItem = new SettingsItem("Job Manager Settings", "", "");
            contProv1.addAllConfigValues(jobMgrItem, jobMgrSettings1);
            input1.add(jobMgrItem);
        }
        if (jobMgrSettings2 != null) {
            SettingsItem jobMgrItem = new SettingsItem("Job Manager Settings", "", "");
            contProv2.addAllConfigValues(jobMgrItem, jobMgrSettings2);
            input2.add(jobMgrItem);
        }
        contProv1.setElements(input1.toArray(new SettingsItem[input1.size()]));
        contProv2.setElements(input2.toArray(new SettingsItem[input2.size()]));
        SettingsItem[] leftItems = (SettingsItem[]) contProv1.getElements(null);
        SettingsItem[] rightItems = (SettingsItem[]) contProv2.getElements(null);
        NodeSettingsTreeViewer.colorItems(leftItems, rightItems);
        refresh();
    }

    public void setElements(Object[] input1, Object[] input2) {
        ((NodeSettingsTreeContentProvider) m_left.getContentProvider()).setElements(input1);
        ((NodeSettingsTreeContentProvider) m_right.getContentProvider()).setElements(input2);
    }

}
