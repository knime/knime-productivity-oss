package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.structuremergeviewer.DiffTreeViewer;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.PlatformUI;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowContainer;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowElement;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowMetaNode;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowSubNode;
import com.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffClearFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilterContribution;
import com.knime.workbench.workflowdiff.editor.filters.StructDiffAdditionsOnlyFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.StructDiffHideEqualNodesFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.StructureDiffFilter;

/**
 * Viewer for the top area in the differ editor. Displaying the nodes of the two flows in a two-column tree.
 * 
 * Because this view allows diffing of elements in two different rows, things are a bit non-standard.
 */
public class WorkflowStructureViewer2 extends DiffTreeViewer implements IFilterableTreeViewer {

	/**
	 * Displays both parts - left and right, creates the overlay/highlighting for selected elements and creates the
	 * tooltip text.
	 */
	class WorkflowStructViewerLabelProvider extends StyledCellLabelProvider {

		private final Image m_selOverlayImg = ImageRepository.getIconImage(SharedImages.Ok);

		/**
		 * Without this update implementation things don't refresh properly.
		 */
		@Override
		public void update(ViewerCell cell) {
			Object element = cell.getElement();
			int colIdx = cell.getColumnIndex();
			String txt = getColumnText(element, colIdx);
			cell.setText(txt);
			cell.setImage(getColumnImage(element, colIdx));
			StyleRange cellStyledRange = new StyleRange(0, txt.length(), getForeground(element, colIdx), getBackground(
					element, colIdx));
			cellStyledRange.font = getFont(element, colIdx);
			StyleRange[] range = { cellStyledRange };
			cell.setStyleRanges(range);
			super.update(cell);
		}

		@Override
		protected void paint(Event event, Object element) {
			super.paint(event, element);
			// overlay icon if node is selected
			int idx = event.index;
			if (idx == 0 && m_leftNode != null && element == m_leftNode) {
				Rectangle b = event.getBounds();
				event.gc.drawImage(m_selOverlayImg, b.x, b.y);
			}
			if (idx == 1 && m_rightNode != null && element == m_rightNode) {
				Rectangle b = event.getBounds();
				event.gc.drawImage(m_selOverlayImg, b.x, b.y);
			}

		}

		private int m_lastToolTipTextLocation = -1;

		public void setLastToolTipTextColumn(final int colIdx) {
			m_lastToolTipTextLocation = colIdx;
		}

		@Override
		public String getToolTipText(Object element) {
			if (element instanceof FlowDiffNode) {
				FlowElement e = null;
				if (m_lastToolTipTextLocation == 0) {
					e = (FlowElement) ((FlowDiffNode) element).getLeft();
				} else if (m_lastToolTipTextLocation == 1) {
					e = (FlowElement) ((FlowDiffNode) element).getRight();
				}
				String annoText = "";
				if (e instanceof FlowNode) {
					annoText = ((FlowNode) e).getNode().getNodeAnnotation().getText();
				} else if (e instanceof FlowMetaNode) {
					annoText = ((FlowMetaNode) e).getWorkflowManager().getNodeAnnotation().getText();
				} else if (e instanceof FlowSubNode) {
					annoText = ((FlowSubNode) e).getWorkflowManager().getNodeAnnotation().getText();
				}
				if (annoText.trim().isEmpty()) {
					annoText = ((FlowDiffNode) element).getName();
				}
				return annoText;
			}
			return super.getToolTipText(element);
		}

		@Override
		public int getToolTipDisplayDelayTime(Object object) {
			return 50;
		}

		public Color getBackground(Object element, int columnIndex) {
			if (element instanceof FlowDiffNode) {
				if (columnIndex == 0 && m_leftNode == element) {
					return SELECTED_BG;
				}
				if (columnIndex == 1 && m_rightNode == element) {
					return SELECTED_BG;
				}
				int kind = ((FlowDiffNode) element).getKind();
				if (kind == Differencer.CHANGE) {
					return NodeSettingsTreeViewer.CONTAINSDIFF;
				}
			}
			return NodeSettingsTreeViewer.DEFAULT;
		}

		public Color getForeground(Object element, int columnIndex) {
			return null;
		};

		public Image getColumnImage(Object element, int columnIndex) {
			if (element instanceof FlowDiffNode) {
				if (columnIndex == 0 && ((FlowDiffNode) element).getLeft() == null) {
					return null;
				}
				if (columnIndex == 1 && ((FlowDiffNode) element).getRight() == null) {
					return null;
				}
			}
			return getImage(element);
		}

		public String getColumnText(Object element, int columnIndex) {
			if (element instanceof FlowDiffNode) {
				FlowElement f = null;
				if (columnIndex == 0) {
					f = (FlowElement) ((FlowDiffNode) element).getLeft();
				}
				if (columnIndex == 1) {
					f = (FlowElement) ((FlowDiffNode) element).getRight();
				}
				if (f != null) {
					return f.getName();
				} else {
					return "";
				}
			}
			return getText(element);
		}

		public String getText(Object element) {
			if (element instanceof IDiffElement) {
				return ((IDiffElement) element).getName();
			}
			return "<unknown>";
		}

		public Image getImage(Object element) {
			if (element instanceof IDiffElement) {
				Image img = ((IDiffElement) element).getImage();
				return img;
			}
			return null;
		}

		public Font getFont(Object element, int columnIndex) {
			if (columnIndex == 0 && m_leftNode == element) {
				return SELECTED_FONT;
			}
			if (columnIndex == 1 && m_rightNode == element) {
				return SELECTED_FONT;
			}
			return DEFAULT_FONT;
		}
	}

	class ToolTipHelper extends ColumnViewerToolTipSupport {
		private final WorkflowStructureViewer2 m_viewer;

		public ToolTipHelper(final WorkflowStructureViewer2 viewer) {
			super(viewer, ToolTip.NO_RECREATE, false);
			m_viewer = viewer;
		}

		@Override
		protected boolean shouldCreateToolTip(Event event) {
			/*
			 * a bit of an evil hack: as we want to display different tooltips in different columns, we need to let the
			 * label provider know in which column the cursor is in. The super class doesn't provide that info - so we
			 * set it in the provider before the super calls it to get the tool tip text. Ouch.
			 */
			IBaseLabelProvider lProv = m_viewer.getLabelProvider();
			if (lProv instanceof WorkflowStructViewerLabelProvider) {
				ViewerCell cell = m_viewer.getCell(new Point(event.x, event.y));
				if (cell != null) {
					((WorkflowStructViewerLabelProvider) lProv).setLastToolTipTextColumn(cell.getColumnIndex());
				}
			}
			return super.shouldCreateToolTip(event);
		}
	}

	private final WorkflowStructViewerLabelProvider m_labelProv;

	private final WorkflowCompareConfiguration m_config;

	/** variables for visual feedback of selection. */
	public static final Color SELECTED_BG = new Color(Display.getDefault(), 203, 232, 246);

	private Font SELECTED_FONT;

	private Font DEFAULT_FONT;

	private FlowDiffNode m_leftNode;

	private FlowDiffNode m_rightNode;

	/**
	 * Creates a new viewer under the given SWT parent and with the specified configuration.
	 * 
	 * @param parent
	 *            the SWT control under which to create the viewer
	 * @param configuration
	 *            the configuration for this viewer
	 */
	public WorkflowStructureViewer2(Composite parent, WorkflowCompareConfiguration configuration) {
		super(new Tree(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.HIDE_SELECTION), configuration);

		m_config = configuration;

		// make it a multi column tree
		Tree tree = getTree();
		tree.setHeaderVisible(true);
		TreeColumn left = new TreeColumn(tree, SWT.LEFT);
		left.setText(configuration.getLeftLabel(null));
		left.setWidth(200);
		TreeColumn right = new TreeColumn(tree, SWT.LEFT);
		right.setText(configuration.getRightLabel(null));
		right.setWidth(200);
		// overwrite label provider for two column view and our fancy selection
		m_labelProv = new WorkflowStructViewerLabelProvider();
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				handleMouseUp(e);
			}
		});
		DEFAULT_FONT = tree.getFont();
		FontData fontData = DEFAULT_FONT.getFontData()[0];
		fontData.setStyle(SWT.BOLD);
		SELECTED_FONT = new Font(tree.getDisplay(), fontData);
		new ToolTipHelper(this);
		setLabelProvider(m_labelProv);
		final Menu menu = new Menu(tree);
		tree.setMenu(menu);
		tree.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				createContextMenu(e, menu);
			}
		});
	}

	@Override
	public String getTitle() {
		return "Workflow Comparison";
	}

	protected void createContextMenu(MenuDetectEvent e, final Menu menu) {
		// clear the menu first
		for (MenuItem i : menu.getItems()) {
			i.dispose();
		}

		boolean enable = false;
		boolean collapse = false;
		final Object sel;
		Item[] selection = getSelection(getTree());
		if (selection.length == 1) {
			Item i = selection[0];
			sel = i.getData();
			if (sel instanceof FlowDiffNode) {
				if (((FlowDiffNode) sel).getLeft() instanceof FlowContainer
						|| ((FlowDiffNode) sel).getRight() instanceof FlowContainer) {
					enable = true;
				}
			}
			if (getExpandedState(sel)) {
				collapse = true;
			}
		} else {
			sel = null;
		}
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(collapse ? "Collapse " : "Expand");
		item.setImage(ImageRepository.getIconImage(collapse ? SharedImages.CollapseAll : SharedImages.Expand));
		if (collapse) {
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					collapseToLevel(sel, ALL_LEVELS);
				}
			});
		} else {
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					expandToLevel(sel, 1);
				}
			});
		}
		item.setEnabled(enable);

		item = new MenuItem(menu, SWT.PUSH);
		item.setText("Compare Highlighted");
		item.setImage(ImageRepository.getIconImage(SharedImages.WorkflowDiffIcon));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleOpen(null);
			}
		});
		item.setEnabled(m_config.getLeftSelection() != null && m_config.getRightSelection() != null);
	}

	protected void handleMouseUp(final MouseEvent e) {
		if (e.button != 1) {
			return;
		}
		ViewerCell cell = getCell(new Point(e.x, e.y));
		if (cell == null) {
			return;
		}
		int colIdx = cell.getColumnIndex();
		FlowDiffNode diff = (FlowDiffNode) cell.getItem().getData();
		if (colIdx < 0 || colIdx > 1) {
			return;
		}
		if (diff.getLeft() instanceof FlowContainer || diff.getRight() instanceof FlowContainer) {
			// expand/collapse meta nodes on click
			if (getExpandedState(diff)) {
				collapseToLevel(diff, 1);
			} else {
				expandToLevel(diff, 1);					
			}
		} else {
			if (colIdx == 0 && diff.getLeft() != null) {
				m_config.setLeftSelection(diff);
				m_leftNode = diff;
				if (m_rightNode == null && diff.getRight() instanceof FlowNode) {
					// first selection selects both
					m_config.setRightSelection(diff);
					m_rightNode = diff;
				}
			}
			if (colIdx == 1 && diff.getRight() != null) {
				m_config.setRightSelection(diff);
				m_rightNode = diff;
				if (m_leftNode == null && diff.getLeft() instanceof FlowNode) {
					m_config.setLeftSelection(diff);
					m_leftNode = diff;
				}
			}
		}
		refresh();
	}

	protected void createToolItems(final ToolBarManager toolBarManager) {
		super.createToolItems(toolBarManager);
		toolBarManager.add(new DiffSelectionButton(this));
		toolBarManager.add(new Separator());
		StructureDiffFilter filter = new StructureDiffFilter();
		toolBarManager.add(new StructDiffAdditionsOnlyFilterButton(filter, this));
		toolBarManager.add(new StructDiffHideEqualNodesFilterButton(filter, this));
		toolBarManager.add(new Separator());
		NodeDiffFilterContribution searchTextField = new NodeDiffFilterContribution(filter, this);
		toolBarManager.add(searchTextField);
		toolBarManager.add(new NodeDiffClearFilterButton(searchTextField));
	}

	public WorkflowCompareConfiguration getCompareConfiguration() {
		return m_config;
	}

	@Override
	public void handleOpen(SelectionEvent event) {
		if(getCompareConfiguration().getContViewer() != null){
			getCompareConfiguration().getContViewer().updateContent(null, null, null);
			getCompareConfiguration().getContViewer().refresh();
		}
		if (m_config.getLeftSelection() == null || m_config.getRightSelection() == null) {
			MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					"No Nodes Selected", "Please select two nodes you wish to compare.");
			return;
		} else {
			super.handleOpen(event);
		}
	}

	@Override
	public void setFilterIcon(boolean showIt) {
		Image searchImg = showIt ? ImageRepository.getIconImage(SharedImages.FunnelIcon) : null;
		for (TreeColumn col : getTree().getColumns()) {
			col.setImage(searchImg);
		}

	}

	@Override
	public String getMatchLabel(IHierMatchableItem item, int col) {
		if (item == getInput()) { // top level is the flow name.
			return null;
		}
		return m_labelProv.getColumnText(item, col);
	}

	@Override
	public int getMatchNumOfColumns() {
		return 2;
	}
}
