package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.INavigatable;
import org.eclipse.compare.structuremergeviewer.DiffTreeViewer;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableFontProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.deferred.SetModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.custom.TreeCursor;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowElement;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;
import com.knime.workbench.workflowdiff.editor.WorkflowCompareEditorInput.FlowDiffNode;
import com.knime.workbench.workflowdiff.editor.filters.IFilterableTreeViewer;
import com.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffClearFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.NodeDiffFilterContribution;
import com.knime.workbench.workflowdiff.editor.filters.StructDiffAdditionsOnlyFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.StructDiffHideEqualNodesFilterButton;
import com.knime.workbench.workflowdiff.editor.filters.StructureDiffFilter;

/**
 * Viewer for the top area in the differ editor. Displaying the nodes of the two flows in a two-column tree.
 */
public class WorkflowStructureViewer2 extends DiffTreeViewer implements IFilterableTreeViewer {

	/**
	 * Displays both parts - left and right
	 */
	class WorkflowStructViewerLabelProvider extends LabelProvider implements
			ITableLabelProvider, ITableColorProvider, ITableFontProvider {

		@Override
		public Color getBackground(Object element, int columnIndex) {
			if (element instanceof FlowDiffNode) {
				if (columnIndex == 0 && m_leftNode == element) {
					return SELECTED_BG;
				}
				if (columnIndex == 1 && m_rightNode == element) {
					return SELECTED_BG;
				}
				int kind = ((FlowDiffNode) element).getKind();
				if (kind == Differencer.CHANGE || kind == Differencer.ADDITION || kind == Differencer.DELETION) {
					return NodeSettingsTreeViewer.CONTAINSDIFF;
				}
			}
			return NodeSettingsTreeViewer.DEFAULT;
		}

		@Override
		public Color getForeground(Object element, int columnIndex) {
			// TODO Auto-generated method stub
			return null;
		};

		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			if (element instanceof FlowDiffNode) {
				if (columnIndex == 0
						&& ((FlowDiffNode) element).getLeft() == null) {
					return null;
				}
				if (columnIndex == 1
						&& ((FlowDiffNode) element).getRight() == null) {
					return null;
				}
			}
			return getImage(element);
		}

		@Override
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
				return ((IDiffElement) element).getImage();
			}
			return null;
		}

		@Override
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
	
	private final WorkflowStructViewerLabelProvider m_labelProv;

	private final WorkflowCompareConfiguration m_config;
	
	/** variables for visual feedback of selection. */
	public static final Color SELECTED_BG = new Color(Display.getDefault(), 203, 232, 246);
	
	private Font SELECTED_FONT;
	
	private Font DEFAULT_FONT;

	private FlowDiffNode m_leftNode;
	
	private FlowDiffNode m_rightNode;
	
	/**
	 * Creates a new viewer under the given SWT parent and with the specified
	 * configuration.
	 * 
	 * @param parent
	 *            the SWT control under which to create the viewer
	 * @param configuration
	 *            the configuration for this viewer
	 */
	public WorkflowStructureViewer2(Composite parent,
			WorkflowCompareConfiguration configuration) {
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
		// overwrite label provider for two column view
		m_labelProv = new WorkflowStructViewerLabelProvider();
		setLabelProvider(m_labelProv);
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
	}

	protected void handleMouseUp(final MouseEvent e) {		
		doSelectCell(e.x, e.y);
	}

	protected void doSelectCell(final int xCoord, final int yCoord) {
		Point pt = new Point(xCoord, yCoord);
		Tree tree = getTree();
		TreeItem item = tree.getItem(pt);
		if (item == null) {
			return;
		}
		if (!(item.getData() instanceof FlowDiffNode)) {
			return;
		}
		FlowDiffNode diff = (FlowDiffNode) item.getData();
		int lineWidth = tree.getLinesVisible() ? tree.getGridLineWidth() : 0;
		int columnCount = tree.getColumnCount();
		int colIdx = -1;
		if (columnCount > 0) {
			for (int i = 0; i < columnCount; i++) {
				Rectangle rect = item.getBounds(i);
				rect.width += lineWidth;
				rect.height += lineWidth;
				if (rect.contains(pt)) {
					colIdx = i;
					break;
				}
			}
		} 
		if (colIdx < 0 || colIdx > 1) {
			return;
		}
		if (colIdx == 0 && diff.getLeft() != null) {
			m_config.setLeftSelection(diff);
			m_leftNode = diff;
		} 
		if (colIdx == 1 && diff.getRight() != null) {
			m_config.setRightSelection(diff);
			m_rightNode = diff;
		}
	}

    protected void createToolItems(final ToolBarManager toolBarManager) {
        super.createToolItems(toolBarManager);
        StructureDiffFilter filter = new StructureDiffFilter();
		NodeDiffFilterContribution searchTextField =
            new NodeDiffFilterContribution(filter, this);
        toolBarManager.add(searchTextField);
        toolBarManager.add(new NodeDiffClearFilterButton(searchTextField));
        toolBarManager.add(new StructDiffAdditionsOnlyFilterButton(filter, this));
        toolBarManager.add(new StructDiffHideEqualNodesFilterButton(filter, this));
    }
    
    @Override
    public void setFilterIcon(boolean showIt) {
		Image searchImg = showIt ? ImageRepository.getImage(SharedImages.FunnelIcon) : null;
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
