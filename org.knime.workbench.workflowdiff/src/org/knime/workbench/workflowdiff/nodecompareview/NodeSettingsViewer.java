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
 */
package org.knime.workbench.workflowdiff.nodecompareview;

import static org.knime.core.ui.wrapper.Wrapper.unwrapNC;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.ui.wrapper.Wrapper;
import org.knime.workbench.editor2.WorkflowEditor;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;
import org.knime.workbench.workflowdiff.editor.filters.NodeDiffClearFilterButton;
import org.knime.workbench.workflowdiff.editor.filters.NodeDiffFilter;
import org.knime.workbench.workflowdiff.editor.filters.NodeDiffFilterContribution;

public class NodeSettingsViewer extends ViewPart {

	public static final String ID = "org.knime.workbench.workflowdiff.nodeSettingsView";

	private NodeSettingsMergeViewer m_mergeViewer;
	private WorkflowEditor m_editor;
	private NodeContainerEditPart m_leftNCEP;
	private NodeContainerEditPart m_rightNCEP;
	private Action[] toolbarActions;

	@Override
	public void createPartControl(Composite parent) {
		FillLayout layout = new FillLayout();
		parent.setLayout(layout);
		m_mergeViewer = new NodeSettingsMergeViewer(parent, SWT.FULL_SELECTION | SWT.BORDER);

		createToolbar();

		getSite().setSelectionProvider(m_mergeViewer.getTreeViewer2());
	}

	private void createToolbar() {
		// get Toolbar
		IActionBars actionBars = getViewSite().getActionBars();
		IToolBarManager toolBar = actionBars.getToolBarManager();
		toolbarActions = new Action[2];

		// Search Field
		NodeDiffFilterContribution searchTextField = new NodeDiffFilterContribution(new NodeDiffFilter(),
				m_mergeViewer.getTreeViewer1(), m_mergeViewer.getTreeViewer2());
		toolBar.add(searchTextField);
		
		//Clear Search Button
		toolBar.add(new NodeDiffClearFilterButton(searchTextField));

		// Select Nodes in Workflow Action
		Action syncAction = new SyncNodeCompareAction(this);
		toolBar.add(syncAction);
		syncAction.setEnabled(false);
		toolbarActions[0] = syncAction;

		// Refresh Action
		Action refreshAction = new RefreshNodeCompareAction(this);
		toolBar.add(refreshAction);
		refreshAction.setEnabled(false);
		toolbarActions[1] = refreshAction;
	}

	@Override
	public void setFocus() {
		m_mergeViewer.getControl().setFocus();
	}

	public void refresh() {
		m_mergeViewer.refresh();
	}

	public void setElements(Object[] input1, Object[] input2) {
		m_mergeViewer.setElements(input1, input2);
	}

	public void setElements(NodeContainer nc1, NodeContainer nc2) {
		m_mergeViewer.setElements(nc1, nc2);
	}

	public void setElements(WorkflowEditor editor, NodeContainerEditPart leftNCEP, NodeContainerEditPart rightNCEP) {
		m_editor = editor;
		m_leftNCEP = leftNCEP;
		m_rightNCEP = rightNCEP;
		m_mergeViewer.setElements(unwrapNC(leftNCEP.getNodeContainer()), unwrapNC(rightNCEP.getNodeContainer()));
		for (int i = 0; i < toolbarActions.length; i++) {
			toolbarActions[i].setEnabled(true);
		}
	}

	public WorkflowEditor getEditor() {
		return m_editor;
	}

	public NodeContainerEditPart getLeftNCEP() {
		return m_leftNCEP;
	}

	public NodeContainerEditPart getRightNCEP() {
		return m_rightNCEP;
	}

}