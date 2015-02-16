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
 *   13.10.2014 (ohl): created
 */
package com.knime.workbench.workflowdiff.editor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareViewerPane;
import org.eclipse.compare.CompareViewerSwitchingPane;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.Splitter;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.util.LockFailedException;
import org.knime.core.util.ProgressMonitorAdapter;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowContainer;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowElement;

/**
 *
 * @author ohl
 */
public class WorkflowCompareEditorInput extends CompareEditorInput {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowCompareEditorInput.class);

    private final AbstractExplorerFileStore m_left;

    private final AbstractExplorerFileStore m_right;

    private FlowElement m_leftTree;

    private FlowElement m_rightTree;


    public WorkflowCompareEditorInput(final AbstractExplorerFileStore left, final AbstractExplorerFileStore right,
        final CompareConfiguration config) {
        super(config);
        m_left = left;
        m_right = right;
        try {
            if (m_left.toLocalFile() == null) {
                throw new IllegalArgumentException("Only local workflows can be compared, " + m_left.getName()
                    + " can't be resolved to a local file.");
            }
            if (m_right.toLocalFile() == null) {
                throw new IllegalArgumentException("Only local workflows can be compared, " + m_right.getName()
                    + " can't be resolved to a local file.");
            }
        } catch (CoreException e) {
            LOGGER.error(e);
            throw new IllegalArgumentException("Error while resolving workflow to local file: " + e.getMessage());
        }
        config.setLeftLabel(m_left.getMountIDWithFullPath());
        config.setRightLabel(m_right.getMountIDWithFullPath());
    }

    @Override
    public Viewer createDiffViewer(Composite parent) {
    	// TODO Auto-generated method stub
    	return new WorkflowStructureViewer(parent, getCompareConfiguration());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Viewer findContentViewer(final Viewer oldViewer, final ICompareInput input, final Composite parent) {
        if (input instanceof FlowDiffNode) {
            return new NodeSettingsMergeViewer(parent, SWT.NONE, getCompareConfiguration());
        }
        return super.findContentViewer(oldViewer, input, parent);
    }

    private void createCompareElements(final IProgressMonitor monitor) {
        ExecutionMonitor m = new ExecutionMonitor(new ProgressMonitorAdapter(monitor));
        WorkflowManager left;
        WorkflowManager right;

        disposeWorkflows();

        try {
            left =
                WorkflowManager.ROOT.load(m_left.resolveToLocalFile(), m,
                    new WorkflowLoadHelper(m_left.resolveToLocalFile()), false).getWorkflowManager();
        } catch (IOException | InvalidSettingsException | CanceledExecutionException
                | UnsupportedWorkflowVersionException | LockFailedException | CoreException e) {
            LOGGER.error(e);
            return;
        }
        try {
            right =
                WorkflowManager.ROOT.load(m_right.resolveToLocalFile(), m,
                    new WorkflowLoadHelper(m_right.resolveToLocalFile()), false).getWorkflowManager();
        } catch (IOException | InvalidSettingsException | CanceledExecutionException
                | UnsupportedWorkflowVersionException | LockFailedException | CoreException e) {
            LOGGER.error(e);
            return;
        }
        FlowStructureCreator creator =
            new FlowStructureCreator("Workflow Compare: " + m_left.getName() + " - " + m_right.getName());
        m_leftTree = creator.getStructure(left.getID());
        m_rightTree = creator.getStructure(right.getID());
        return;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleDispose() {
        disposeWorkflows();
        super.handleDispose();
    }

    private void disposeWorkflows() {
        if (m_leftTree instanceof FlowContainer) {
            WorkflowManager wfm = ((FlowContainer)m_leftTree).getWorkflowManager();
            wfm.getParent().removeProject(wfm.getID());
        }
        if (m_rightTree instanceof FlowContainer) {
            WorkflowManager wfm = ((FlowContainer)m_rightTree).getWorkflowManager();
            wfm.getParent().removeProject(wfm.getID());
        }
        m_leftTree = null;
        m_rightTree = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object prepareInput(final IProgressMonitor monitor) throws InvocationTargetException,
        InterruptedException {

        try {

            monitor.beginTask("Loading and comparing workflows (" + m_left.getName() + " and " + m_right.getName()
                + ")", IProgressMonitor.UNKNOWN);

            setTitle("Compare of " + m_left.getMountIDWithFullPath() + " and " + m_right.getMountIDWithFullPath());

            Differencer d = new Differencer() {
                @Override
                protected Object visit(final Object parent, final int description, final Object ancestor,
                    final Object left, final Object right) {
                    return new FlowDiffNode((IDiffContainer)parent, description, (ITypedElement)ancestor,
                        (ITypedElement)left, (ITypedElement)right);
                }
            };

            createCompareElements(monitor);
            if (monitor.isCanceled()) {
                return null;
            }
            if (m_leftTree == null || m_rightTree == null) {
                LOGGER.warn("Unable to perform compare: Internal structures not initialized (user canceled??)");
                return null;
            }
            Object root = d.findDifferences(false, monitor, null, null, m_leftTree, m_rightTree);
            return root;

        } finally {
            monitor.done();
        }
    }

    /**
     * Using our own class to be able to distinguish from others and to instantiate our own viewer.
     *
     * @author ohl
     */
    class FlowDiffNode extends DiffNode {

        private boolean fDirty = false;

        private ITypedElement fLastId;

        private String fLastName;

        public FlowDiffNode(final IDiffContainer parent, final int description, final ITypedElement ancestor,
            final ITypedElement left, final ITypedElement right) {
            super(parent, description, ancestor, left, right);
        }

        //        public void fireChange() {
        //            super.fireChange();
        //            setDirty(true);
        //            fDirty= true;
        //            if (fDiffViewer != null)
        //                fDiffViewer.refresh(this);
        //        }
        void clearDirty() {
            fDirty = false;
        }

        @Override
        public String getName() {
            if (fLastName == null) {
                fLastName = super.getName();
            }
            if (fDirty) {
                return '<' + fLastName + '>';
            }
            return fLastName;
        }

        @Override
        public ITypedElement getId() {
            ITypedElement id = super.getId();
            if (id == null) {
                return fLastId;
            }
            fLastId = id;
            return id;
        }

    }
}
