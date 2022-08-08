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
 */
package org.knime.workbench.workflowdiff.editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;

import javax.swing.tree.TreeNode;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.Node;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.config.Config;
import org.knime.core.node.config.base.AbstractConfigEntry;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.SubNodeContainer;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;

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
            if (nc instanceof SubNodeContainer) {
                String contentHash = getContentSha1Hash((SubNodeContainer) nc);
                input.add(new SettingsItem("Component Content Hash", "SHA-1", contentHash));
                String settingsHash = getSettingsSha1Hash(nc);
                input.add(new SettingsItem("Component Internal Settings Hash", "SHA-1", settingsHash));
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

    static String getContentSha1Hash(SubNodeContainer nc) {
        // first 8 chars -- similar to git hashes
        return DigestUtils.sha1Hex(getContentString(nc).getBytes()).substring(0, 7);
    }

    private static String getContentString(NodeContainer nc) {
        Collection<NodeContainer> nodeContainers = null;
        ArrayList<String> nodes = new ArrayList<String>();
        if (nc instanceof WorkflowManager) {
            nodeContainers = ((WorkflowManager) nc).getNodeContainers();
        } else if (nc instanceof SubNodeContainer) {
            nodeContainers = ((SubNodeContainer) nc).getNodeContainers();
        } else {
            return nc.getName() + nc.getID().getIndex();
        }
        for (NodeContainer nc2 : nodeContainers) {
            nodes.add(getContentString(nc2));
        }
        Collections.sort(nodes);
        nodes.add(0, nc.getName() + nc.getID().getIndex());
        nodes.add(";;;");
        return nodes.stream().collect(Collectors.joining(";"));
    }

    static String getSettingsSha1Hash(NodeContainer nc) {
        // first 8 chars -- similar to git hashes
        return DigestUtils.sha1Hex(getAllSettingsArray(nc)).substring(0, 7);
    }

    private static byte[] getAllSettingsArray(NodeContainer nc) {
        Collection<NodeContainer> nodeContainers = null;
        ArrayList<NodeContainer> nodes = new ArrayList<>();
        if (nc instanceof WorkflowManager) {
            nodeContainers = ((WorkflowManager) nc).getNodeContainers();
        } else if (nc instanceof SubNodeContainer) {
            nodeContainers = ((SubNodeContainer) nc).getNodeContainers();
        } else {
            return getSingleSettingsArray(nc);
        }
        for (NodeContainer nc2 : nodeContainers) {
            nodes.add(nc2);
        }
        Collections.sort(nodes, (node1, node2) -> {
            return (node1.getName() + node1.getID().getIndex()).compareTo(node2.getName() + node2.getID().getIndex());
        });
        byte[] byteArray;
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            for (int i = 0; i < nodes.size(); i++) {
                stream.write(getAllSettingsArray(nodes.get(i)));
            }
            byteArray = stream.toByteArray();
        } catch (IOException e) {
            byteArray = e.getMessage().getBytes();
        }
        return byteArray;
    }

    private static byte[] getSingleSettingsArray(NodeContainer nc) {
        byte[] settingsArray;
        NodeSettings settings = new NodeSettings("tmp");
        try {
            nc.getParent().saveNodeSettings(nc.getID(), settings);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            settings.saveToXML(byteArrayOutputStream);
            if (nc instanceof SubNodeContainer) {
                String hash = NodeSettingsTreeContentProvider.getContentSha1Hash((SubNodeContainer) nc);
                byteArrayOutputStream.write(hash.getBytes());
            }
            settingsArray = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            String msg = "Error while storing node settings: " + e.getMessage();
            settingsArray = msg.getBytes();
        }
        return settingsArray;
    }
}