/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */
package com.knime.workbench.workflowdiff.actions;

import java.util.Iterator;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.view.ContentDelegator;
import org.knime.workbench.explorer.view.ExplorerView;
import org.knime.workbench.explorer.view.actions.ExplorerAction;

import com.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput;

/**
 * Action for diffing two flows.
 *
 * @author Peter Ohl, KNIME.com AG, Zurich, Switzerland
 */
public class WorkflowDiffAction implements IObjectActionDelegate {
    /** ID of the global rename action in the explorer menu. */
    public static final String FLOWDIFF_ACTION_ID = "com.knime.workbench.workflowdiff.actions.WorkflowDiffAction";

    private IStructuredSelection m_selection = StructuredSelection.EMPTY;
    
    private IStructuredSelection getSelection() {
    	return m_selection;
	}
    
    private boolean canRun() {
        IStructuredSelection selection = getSelection();
        if (selection.size() != 2 && selection.size() != 1) {
            return false;
        }
        Iterator selIter = selection.iterator();
        AbstractExplorerFileStore first = ContentDelegator.getFileStore(selIter.next());
        AbstractExplorerFileStore second = first;
        if (selection.size() == 2) {
        	second = ContentDelegator.getFileStore(selIter.next());
        }
        return AbstractExplorerFileStore.isWorkflow(first) && AbstractExplorerFileStore.isWorkflow(second);
    }

    public void run() {
        IStructuredSelection selection = getSelection();
        if (selection.size() != 2 && selection.size() != 1) {
        	Assert.isTrue(false);
        	return;
        }
        Iterator selIter = selection.iterator();
        AbstractExplorerFileStore first = ContentDelegator.getFileStore(selIter.next());
        AbstractExplorerFileStore second = first;
        if (selection.size() == 2) {
        	second = ContentDelegator.getFileStore(selIter.next());
        }
        if (!AbstractExplorerFileStore.isWorkflow(first) || !AbstractExplorerFileStore.isWorkflow(second)) {
            Assert.isTrue(false);
            return;
        }

        CompareConfiguration conf = new CompareConfiguration();
        conf.setRightEditable(false);
        conf.setLeftEditable(false);
        WorkflowCompareEditorInput input = new WorkflowCompareEditorInput(first, second, conf);

        CompareUI.openCompareEditor(input);
    }

	@Override
	public void run(IAction action) {
		if (action.isEnabled()) {
			run();
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		if (selection != null && (selection instanceof IStructuredSelection)) {
			m_selection = (IStructuredSelection) selection;
		} else {
			m_selection = StructuredSelection.EMPTY;
		}
		boolean canRun = canRun();
		action.setEnabled(canRun);
	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}
}
