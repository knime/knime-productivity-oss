package com.knime.workbench.workflowdiff.nodecompareview;

import org.eclipse.gef.GraphicalViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.PlatformUI;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

public class SyncNodeCompareAction extends Action {

	private final NodeSettingsViewer m_viewer;

	public SyncNodeCompareAction(NodeSettingsViewer viewer) {
		super();
		m_viewer = viewer;
		setText("Select in Workflow");
		setDescription("Select in Workflow");
		setToolTipText(getDescription());
		setImageDescriptor(ImageRepository.getImageDescriptor(SharedImages.Synch));
	}

	@Override
	public void run() {
		GraphicalViewer viewer = m_viewer.getEditor().getViewer();
		viewer.select(m_viewer.getLeftNCEP());
		viewer.appendSelection(m_viewer.getRightNCEP());
		viewer.reveal(m_viewer.getRightNCEP());
		viewer.reveal(m_viewer.getLeftNCEP());
		viewer.setFocus(m_viewer.getLeftNCEP());
		PlatformUI.getWorkbench().getWorkbenchWindows()[0].getPages()[0].activate(m_viewer.getEditor());
	}

}
