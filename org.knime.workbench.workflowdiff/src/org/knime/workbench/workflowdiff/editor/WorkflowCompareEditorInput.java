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
package org.knime.workbench.workflowdiff.editor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.NodeContainerTemplate;
import org.knime.core.node.workflow.SubNodeContainer;
import org.knime.core.node.workflow.TemplateNodeContainerPersistor;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowCreationHelper;
import org.knime.core.node.workflow.WorkflowEvent;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.MetaNodeLinkUpdateResult;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.util.LockFailedException;
import org.knime.core.util.ProgressMonitorAdapter;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowContainer;
import org.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowElement;

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

	private static WorkflowManager m_templateROOT;

    public WorkflowCompareEditorInput(final AbstractExplorerFileStore left, final AbstractExplorerFileStore right,
        final WorkflowCompareConfiguration config) {
        super(config);
        m_left = left;
        m_right = right;
//        try {
//            if (m_left.toLocalFile() == null) {
//                throw new IllegalArgumentException("Only local workflows can be compared, " + m_left.getName()
//                    + " can't be resolved to a local file.");
//            }
//            if (m_right.toLocalFile() == null) {
//                throw new IllegalArgumentException("Only local workflows can be compared, " + m_right.getName()
//                    + " can't be resolved to a local file.");
//            }
//        } catch (CoreException e) {
//            LOGGER.error(e);
//            throw new IllegalArgumentException("Error while resolving workflow to local file: " + e.getMessage());
//        }
        config.setLeftLabel(m_left.getMountIDWithFullPath());
        config.setRightLabel(m_right.getMountIDWithFullPath());
    }

    @Override
    public Viewer createDiffViewer(Composite parent) {
    	return new WorkflowStructureViewer2(parent, (WorkflowCompareConfiguration) getCompareConfiguration());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Viewer findContentViewer(final Viewer oldViewer, final ICompareInput input, final Composite parent) {
        if (input instanceof FlowDiffNode) {
        	NodeSettingsMergeViewer contViewer = new NodeSettingsMergeViewer(parent, SWT.NONE, getCompareConfiguration());
			((WorkflowCompareConfiguration)getCompareConfiguration()).setContViewer(contViewer);
        	return contViewer;
        }
        return super.findContentViewer(oldViewer, input, parent);
    }

    private void createCompareElements(final IProgressMonitor monitor) {
        ExecutionMonitor m = new ExecutionMonitor(new ProgressMonitorAdapter(monitor));
        final WorkflowManager left;
        final WorkflowManager right;

        disposeWorkflows();
		FlowStructureCreator creator = new FlowStructureCreator(
				"Workflow Compare: " + m_left.getName() + " - " + m_right.getName());
        try {
			if (!AbstractExplorerFileStore.isWorkflowTemplate(m_left)) {
				File leftLocalFile = m_left.toLocalFile();
				if (leftLocalFile == null) {
					LOGGER.debug("Downloading flow for comparison: " + m_left.getMountIDWithFullPath());
					leftLocalFile = m_left.resolveToLocalFile(monitor);
				}
				WorkflowLoadResult leftLoadResult = WorkflowManager.ROOT.load(leftLocalFile, m,
						new WorkflowLoadHelper(leftLocalFile), false);
				left = leftLoadResult.getWorkflowManager();
				m_leftTree = creator.getStructure(left.getID());
			} else {
				URI sourceURI = m_left.toURI();
				File parentFile = ResolverUtil.resolveURItoLocalOrTempFile(sourceURI, monitor);
				WorkflowLoadHelper loadHelper = new WorkflowLoadHelper(true);
				TemplateNodeContainerPersistor loadPersistor = loadHelper.createTemplateLoadPersistor(parentFile,
						sourceURI);
				MetaNodeLinkUpdateResult loadResult = new MetaNodeLinkUpdateResult(
						"Template from \"" + sourceURI + "\"");
				getTemplateParentWFM().load(loadPersistor, loadResult, new ExecutionMonitor(), false);
				NodeContainerTemplate loadedInstance = loadResult.getLoadedInstance();
				if (loadedInstance instanceof WorkflowManager) {
					left = (WorkflowManager) loadedInstance;
				}else if(loadedInstance instanceof SubNodeContainer){
					left = ((SubNodeContainer) loadedInstance).getWorkflowManager();
				}else{
					left = null;
				}
				m_leftTree = creator.getStructure(left);
			}
        } catch (IOException | InvalidSettingsException | CanceledExecutionException
                | UnsupportedWorkflowVersionException | LockFailedException | CoreException e) {
            LOGGER.error(e);
            return;
        }
        try {
			if (!AbstractExplorerFileStore.isWorkflowTemplate(m_right)) {
				File rightLocalFile = m_right.toLocalFile();
				if (rightLocalFile == null) {
					LOGGER.debug("Downloading flow for comparison: " + m_right.getMountIDWithFullPath());
					rightLocalFile = m_right.resolveToLocalFile(monitor);
				}
				WorkflowLoadResult rightLoadResult = WorkflowManager.ROOT.load(rightLocalFile, m,
						new WorkflowLoadHelper(rightLocalFile), false);
				right = rightLoadResult.getWorkflowManager();
				m_rightTree = creator.getStructure(right.getID());
			} else {
				URI sourceURI = m_right.toURI();
				File parentFile = ResolverUtil.resolveURItoLocalOrTempFile(sourceURI, monitor);
				WorkflowLoadHelper loadHelper = new WorkflowLoadHelper(true);
				TemplateNodeContainerPersistor loadPersistor = loadHelper.createTemplateLoadPersistor(parentFile,
						sourceURI);
				MetaNodeLinkUpdateResult loadResult = new MetaNodeLinkUpdateResult(
						"Template from \"" + sourceURI + "\"");
				getTemplateParentWFM().load(loadPersistor, loadResult, new ExecutionMonitor(), false);
				NodeContainerTemplate loadedInstance = loadResult.getLoadedInstance();
				if (loadedInstance instanceof WorkflowManager) {
					right = (WorkflowManager) loadedInstance;
				}else if(loadedInstance instanceof SubNodeContainer){
					right = ((SubNodeContainer) loadedInstance).getWorkflowManager();
				}else{
					right = null;
				}
				m_rightTree = creator.getStructure(right);
			}
        } catch (IOException | InvalidSettingsException | CanceledExecutionException
                | UnsupportedWorkflowVersionException | LockFailedException | CoreException e) {
            LOGGER.error(e);
            return;
        }
        return;
    }

	/**
	 * @return the templateParentWFM
	 */
	private synchronized static WorkflowManager getTemplateParentWFM() {
		if (m_templateROOT == null) {
			LOGGER.debug("Creating template parent workflow project");
			m_templateROOT = WorkflowManager.ROOT.createAndAddProject("Template-Parent", new WorkflowCreationHelper());
			m_templateROOT.addListener(e -> checkAutoDiscardTemplateWFM(e));
		}
		return m_templateROOT;
	}

	/**
	 * @param e
	 */
	private synchronized static void checkAutoDiscardTemplateWFM(final WorkflowEvent e) {
		if (WorkflowEvent.Type.NODE_REMOVED.equals(e.getType())) {
			if (m_templateROOT!=null&&m_templateROOT.getNodeContainers().isEmpty()) {
				LOGGER.debug("Discarding template parent workflow project");
				WorkflowManager.ROOT.removeProject(m_templateROOT.getID());
				m_templateROOT = null;
			}
		}
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
			if (wfm.isProject()) {
				wfm.getParent().removeProject(wfm.getID());
			} else {
				wfm.getParent().removeNode(wfm.getID());
			}
        }
        if (m_rightTree instanceof FlowContainer) {
            WorkflowManager wfm = ((FlowContainer)m_rightTree).getWorkflowManager();
			if (wfm.isProject()) {
				wfm.getParent().removeProject(wfm.getID());
			} else {
				wfm.getParent().removeNode(wfm.getID());
			}
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

			createCompareElements(monitor);
			if (monitor.isCanceled()) {
				return null;
			}
			if (m_leftTree == null || m_rightTree == null) {
				LOGGER.warn("Unable to perform compare: Internal structures not initialized (user canceled??)");
				return null;
			}
			Differencer d = new WorkflowStructDifferencer();
			Object root = d.findDifferences(false, monitor, null, null, m_leftTree, m_rightTree);
			return root;

		} finally {
			monitor.done();
		}
    }
}
