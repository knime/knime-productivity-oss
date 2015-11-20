package com.knime.workbench.workflowdiff.nodecompareview;

import org.eclipse.jface.action.Action;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

public class RefreshNodeCompareAction extends Action {

	private final NodeSettingsViewer m_viewer;

	public RefreshNodeCompareAction(NodeSettingsViewer viewer) {
		super();
		m_viewer = viewer;
		setText("Refresh");
		setDescription("Refresh");
		setToolTipText(getDescription());
		setImageDescriptor(ImageRepository.getImageDescriptor(SharedImages.Refresh));
	}

	@Override
	public void run() {
		WorkflowManager manager = m_viewer.getEditor().getWorkflowManager();
		NodeID left = m_viewer.getLeftNCEP().getNodeContainer().getID();
		NodeID right = m_viewer.getRightNCEP().getNodeContainer().getID();
		if (manager.containsNodeContainer(left) && manager.containsNodeContainer(right)) {
			m_viewer.setElements(manager.getNodeContainer(m_viewer.getLeftNCEP().getNodeContainer().getID()),
					manager.getNodeContainer(m_viewer.getRightNCEP().getNodeContainer().getID()));
		}
	}

	@Override
	public boolean isEnabled() {
		if(m_viewer.getEditor()!=null){
			return true;
		}
		return false;
	}

}
