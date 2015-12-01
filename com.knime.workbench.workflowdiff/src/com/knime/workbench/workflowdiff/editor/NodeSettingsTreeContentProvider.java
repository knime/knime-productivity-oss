package com.knime.workbench.workflowdiff.editor;

import java.util.ArrayList;
import java.util.Enumeration;

import javax.swing.tree.TreeNode;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.config.base.AbstractConfigEntry;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.WorkflowManager;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;

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
                SettingsItem errItem = new SettingsItem("ERROR", "No settings available", e.getMessage());
                m_settings = new SettingsItem[] { errItem };
                return;
            }

            ArrayList<SettingsItem> input = new ArrayList<SettingsItem>(3);
            SettingsItem modelItem = new SettingsItem("Node Settings", "", "");
            addAllConfigValues(modelItem, modelSettings);
            input.add(modelItem);
            if (memorySettings != null) {
                SettingsItem memoryItem = new SettingsItem("System Node Settings", "", "");
                addAllConfigValues(memoryItem, memorySettings);
                input.add(memoryItem);
            }
            if (jobMgrSettings != null) {
                SettingsItem jobMgrItem = new SettingsItem("Job Manager Settings", "", "");
                addAllConfigValues(jobMgrItem, jobMgrSettings);
                input.add(jobMgrItem);
            }
            m_settings = input.toArray(new SettingsItem[input.size()]);
        } else {
            m_settings = new SettingsItem[0];
        }

    }

    /**
     * Recursively adds all settings from the config as children to the tree
     * item.
     * 
     * @param item
     * @param config
     */
    public void addAllConfigValues(final SettingsItem item, final Config config) {
        for (Enumeration<TreeNode> it = config.children(); it.hasMoreElements();) {
            AbstractConfigEntry prop = (AbstractConfigEntry) it.nextElement();
            if (prop instanceof Config) {
                // sub-config
                SettingsItem subConfig = new SettingsItem(prop.getKey(), "sub-config", "", item);
                addAllConfigValues(subConfig, (Config) prop);
            } else {
                // all settings are displayed as string
                String id = prop.getKey();
                String type = prop.getType().name().substring(1);
                String value = prop.toStringValue();

                new SettingsItem(id, type, value, item);
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

    public void setElements(Object[] newSettings) {
        m_settings = new SettingsItem[newSettings.length];
        for (int i = 0; i < newSettings.length; i++) {
            m_settings[i] = (SettingsItem) newSettings[i];
        }
    }
}