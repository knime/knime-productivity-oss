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
 *   10.10.2014 (ohl): created
 */
package com.knime.workbench.workflowdiff.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.compare.structuremergeviewer.IStructureCreator;
import org.eclipse.core.runtime.Assert;
import org.eclipse.swt.graphics.Image;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.SubNodeContainer;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.virtual.subnode.VirtualSubNodeInputNodeModel;
import org.knime.core.node.workflow.virtual.subnode.VirtualSubNodeOutputNodeModel;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

/**
 *
 * @author ohl
 */
public class FlowStructureCreator implements IStructureCreator {

    /**
     * Common base class for nodes and stuff.
     */
    public static abstract class FlowElement implements IStructureComparator, ITypedElement {

        protected static final String ELEMENT_TYPE_FLOW = "knime.flow";
        protected static final String ELEMENT_TYPE_NODE = "knime.node";
        protected static final String ELEMENT_TYPE_METANODE = "knime.metanode";
        protected static final String ELEMENT_TYPE_SUBNODE = "knime.subnode";

        private final String m_name;

        private final NodeID m_id;
        
        private final URL m_icon;

        FlowElement(final String name, final NodeID id, final URL icon) {
            m_name = name;
            m_id = id;
            m_icon=icon;
        }

        @Override
        public String getName() {
            return m_name + " (" + m_id.getIndex() + ")";
        }

        @Override
        public Image getImage() {
            if (getType() == ELEMENT_TYPE_NODE) {
                return ImageRepository.getIconImage(m_icon);
            } else if (getType() == ELEMENT_TYPE_METANODE) {
                return ImageRepository.getIconImage(SharedImages.MetaNodeDetailed);
            } else if (getType() == ELEMENT_TYPE_SUBNODE) {
                return ImageRepository.getIconImage(SharedImages.SubNodeDetailed);
            } else if (getType() == ELEMENT_TYPE_FLOW) {
                return ImageRepository.getIconImage(SharedImages.Workflow);
            }
            return ImageRepository.getIconImage(SharedImages.Workflow);
        }

        /*
         * Returns true if other is ITypedElement and names are equal.
         * @see IComparator#equals
         */
        @Override
        public boolean equals(final Object other) {
            if (other instanceof FlowElement) {
                return (m_id.getIndex() == ((FlowElement)other).m_id.getIndex())
                    && m_name.equals(((FlowElement)other).m_name);
            }
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return m_id.getIndex() ^ m_name.hashCode();
        }
    }

    public static abstract class FlowContainer extends FlowElement {

        private final WorkflowManager m_wfm;

        FlowContainer(final WorkflowManager wfm) {
            super(wfm.getName(), wfm.getID(), null);
            m_wfm = wfm;
        }

        @Override
        public Object[] getChildren() {
            Collection<NodeContainer> nodes = m_wfm.getNodeContainers();
            ArrayList<Object> children = new ArrayList<Object>();
            for (NodeContainer n : nodes) {
                if (n instanceof WorkflowManager) {
                	children.add(new FlowMetaNode((WorkflowManager)n));
                } else if (n instanceof SubNodeContainer) {
                	children.add(new FlowSubNode((SubNodeContainer)n));
                } else {
                	// if it is not a subnode's input or output node, add it
                	if (!(n instanceof NativeNodeContainer 
                			&& (((NativeNodeContainer) n).isModelCompatibleTo(VirtualSubNodeOutputNodeModel.class) 
                				|| ((NativeNodeContainer) n).isModelCompatibleTo(VirtualSubNodeInputNodeModel.class)))) {
                		children.add(new FlowNode(n));
                	}
                }
            }
            return children.toArray(new Object[children.size()]);
        }

        public WorkflowManager getWorkflowManager() {
            return m_wfm;
        }

        //        /*
        //         * Returns true if other is ITypedElement and names are equal.
        //         * @see IComparator#equals
        //         */
        //        @Override
        //        public boolean equals(final Object other) {
        //            if (other instanceof FlowContainer) {
        //                WorkflowManager otherWfm = ((FlowContainer)other).getWorkflowManager();
        //                if (otherWfm.getNrInPorts() != m_wfm.getNrInPorts()) {
        //                    return false;
        //                }
        //                if (otherWfm.getNrOutPorts() != m_wfm.getNrOutPorts()) {
        //                    return false;
        //                }
        //                for (int i = 0; i < m_wfm.getNrInPorts(); i++) {
        //                    if (!m_wfm.getInPort(i).getPortType().equals(otherWfm.getInPort(i).getPortType())) {
        //                        return false;
        //                    }
        //                    if (!m_wfm.getInPort(i).getPortName().equals(otherWfm.getInPort(i).getPortName())) {
        //                        return false;
        //                    }
        //                }
        //                for (int i = 0; i < m_wfm.getNrOutPorts(); i++) {
        //                    if (!m_wfm.getOutPort(i).getPortType().equals(otherWfm.getOutPort(i).getPortType())) {
        //                        return false;
        //                    }
        //                    if (!m_wfm.getOutPort(i).getPortName().equals(otherWfm.getOutPort(i).getPortName())) {
        //                        return false;
        //                    }
        //                }
        //                return m_wfm.getName().equals(otherWfm.getName());
        //            }
        //            return false;
        //        }
        //
        //        @Override
        //        public int hashCode() {
        //            return (m_wfm.getNrInPorts() << 2) ^ (m_wfm.getNrOutPorts() << 1) ^ m_wfm.getName().hashCode();
        //        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return m_wfm.getNameWithID();
        }
    }

    public static class FlowWorkflow extends FlowContainer {
        public FlowWorkflow(final WorkflowManager flow) {
            super(flow);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return ELEMENT_TYPE_FLOW;
        }
    }

    public static class FlowMetaNode extends FlowContainer {

        public FlowMetaNode(final WorkflowManager wfm) {
            super(wfm);
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return ELEMENT_TYPE_METANODE;
        }
    }

    public static class FlowSubNode extends FlowContainer {
        public FlowSubNode(final SubNodeContainer subflow) {
            super(subflow.getWorkflowManager());
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public String getType() {
            return ELEMENT_TYPE_SUBNODE;
        }
    }

    public static class FlowNode extends FlowElement implements IStreamContentAccessor {

        private byte[] m_xmlSettings;

        private final NodeContainer m_node;

        FlowNode(final NodeContainer node) {
            super(node.getName(), node.getID(), node.getIcon());
            m_node = node;
            NodeSettings settings = new NodeSettings("tmp");
            try {
                node.getParent().saveNodeSettings(node.getID(), settings);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                settings.saveToXML(byteArrayOutputStream);
                m_xmlSettings = byteArrayOutputStream.toByteArray();
            } catch (InvalidSettingsException e1) {
                String msg = "Invalid or no user settings: " + node.getNameWithID() + "(" + e1.getMessage() + ")";
                m_xmlSettings = msg.getBytes();
            } catch (IOException e) {
                String msg = "Error while storing node settings: " + e.getMessage();
                NodeLogger.getLogger(FlowNode.class).error(msg, e);
                m_xmlSettings = msg.getBytes();
            }
        }

        @Override
        public String getType() {
            return ELEMENT_TYPE_NODE;
        }

        @Override
        public Object[] getChildren() {
            return null;
        }

        @Override
        public InputStream getContents() {
            return new ByteArrayInputStream(m_xmlSettings);
        }

        byte[] getBytes() {
            return Arrays.copyOf(m_xmlSettings, m_xmlSettings.length);
        }

        void setBytes(final byte[] buffer) {
            m_xmlSettings = Arrays.copyOf(buffer, buffer.length);
        }

        void appendBytes(final byte[] buffer, final int length) {
            Assert.isTrue(false);
        }

        public NodeContainer getNode() {
            return m_node;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return m_node.getNameWithID();
        }
    }

    private final String m_title;

    /**
     * Create a new ZipFileStructureCreator with the given title. The title is returned by the method
     * <code>getName()</code>.
     *
     * @param title the title of this structure creator
     */
    public FlowStructureCreator(final String title) {
        m_title = title;
    }

    @Override
    public String getName() {
        return m_title;
    }

    @Override
    public FlowElement getStructure(final Object input) {

        if (input instanceof NodeID) {
            WorkflowManager flow = (WorkflowManager)WorkflowManager.ROOT.getNodeContainer((NodeID)input);
            return new FlowWorkflow(flow);
        }

        if (input instanceof WorkflowManager) {
            return new FlowMetaNode((WorkflowManager)input);
        }
        if (input instanceof SubNodeContainer) {
            return new FlowSubNode((SubNodeContainer)input);
        }
        if (input instanceof NodeContainer) {
            return new FlowNode((NodeContainer)input);
        }

        return null;
    }

    @Override
    public String getContents(final Object o, final boolean ignoreWhitespace) {
        Assert.isTrue(false);
        return "";
    }

    /**
     * Don't update/change workflows.
     *
     * @return always false.
     */
    public boolean canSave() {
        return false;
    }

    /**
     */
    @Override
    public void save(final IStructureComparator structure, final Object input) {
        Assert.isTrue(false); // Cannot update zip archive
    }

    @Override
    public IStructureComparator locate(final Object path, final Object source) {
        return null;
    }

    /**
     * Returns <code>false</code> since this <code>IStructureCreator</code> cannot rewrite the diff tree in order to
     * fold certain combinations of additions and deletions.
     * <p>
     * Note: this method is for internal use only. Clients should not call this method.
     *
     * @return <code>false</code>
     */
    public boolean canRewriteTree() {
        return false;
    }

    /**
     * Empty implementation since this <code>IStructureCreator</code> cannot rewrite the diff tree in order to fold
     * certain combinations of additions and deletions.
     * <p>
     * Note: this method is for internal use only. Clients should not call this method.
     *
     * @param differencer
     * @param root
     */
    public void rewriteTree(final Differencer differencer, final IDiffContainer root) {
        // empty default implementation
    }

}
