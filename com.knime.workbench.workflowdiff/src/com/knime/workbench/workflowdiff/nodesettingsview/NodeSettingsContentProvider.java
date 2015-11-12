package com.knime.workbench.workflowdiff.nodesettingsview;

import java.util.Enumeration;

import javax.swing.tree.TreeNode;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.knime.core.node.config.Config;
import org.knime.core.node.config.base.AbstractConfigEntry;

public class NodeSettingsContentProvider implements ITreeContentProvider {

    private NodeSettingsItem[] m_settings = new NodeSettingsItem[0];

    @Override
    public void dispose() {
        m_settings = null;
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
//    	System.out.println("inputChanged");
//        System.out.println("|"+oldInput);
//        System.out.println("|"+newInput);
//        System.out.println("----");
    }

    @Override
    public Object[] getElements(Object inputElement) {
        return m_settings;
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof NodeSettingsItem) {
            return ((NodeSettingsItem) parentElement).getChildren();
        }
        return null;
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof NodeSettingsItem) {
            return ((NodeSettingsItem) element).getParent();
        }
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof NodeSettingsItem) {
            return ((NodeSettingsItem) element).hasChildren();
        }
        return false;
    }

    public void setElements(Object[] newSettings) {
        m_settings = new NodeSettingsItem[newSettings.length];
        for (int i = 0; i < newSettings.length; i++) {
            m_settings[i] = (NodeSettingsItem) newSettings[i];
        }
    }

    /**
     * Recursively adds all settings from the config as children to the tree
     * item.
     *
     * @param item
     * @param config
     */
    public void addAllConfigValues(final NodeSettingsItem item, final Config config) {
        for (Enumeration<TreeNode> it = config.children(); it.hasMoreElements();) {
            AbstractConfigEntry prop = (AbstractConfigEntry) it.nextElement();
            if (prop instanceof Config) {
                // sub-config
                NodeSettingsItem subConfig = new NodeSettingsItem(prop.getKey(), "sub-config", "", item);
                addAllConfigValues(subConfig, (Config) prop);
            } else {
                // all settings are displayed as string
                String id = prop.getKey();
                String type = prop.getType().name().substring(1);
                String value = prop.toStringValue();

                new NodeSettingsItem(id, type, value, item);
            }
        }
    }

}
