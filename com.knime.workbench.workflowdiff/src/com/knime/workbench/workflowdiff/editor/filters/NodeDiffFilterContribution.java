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
 * -------------------------------------------------------------------
 *
 */
package com.knime.workbench.workflowdiff.editor.filters;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;


/**
 * Toolbar contribution with a filter for the view.
 *
 * @author Peter Ohl, KNIME.com AG, Zurich, Switzerland
 */
public class NodeDiffFilterContribution extends ControlContribution implements KeyListener {

    private static final Color FILTERED = new Color(Display.getDefault(), 198, 198, 255);

    private final IFilterableTreeViewer[] m_viewer;

    private Text m_text;

    private final NodeDiffFilter m_filter;

    private static final boolean IS_OS_WINDOWS = Platform.OS_WIN32.equals(Platform.getOS());

    /**
     * Creates the contribution item. The filter is set in the viewer by this class. This can be used in toolbars etc.
     *
     * @param viewer The viewer. 
     * @param filter The filter to use.
     */
    public NodeDiffFilterContribution(final NodeDiffFilter filter,
        final IFilterableTreeViewer... viewer) {
        super("com.knime.workflow.diff.nodesettingsviewerfiltercontribution");
        m_viewer = viewer;
        m_filter = filter;
        for (IFilterableTreeViewer v : m_viewer) {
            v.addFilter(m_filter);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Control createControl(final Composite parent) {
        m_text = new Text(parent, SWT.BORDER);
        m_text.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        m_text.addKeyListener(this);
        return m_text;
    }

    private void keyReleased(final char c) {
        boolean shouldExpand = true;
        String str = m_text.getText();
        if (c == SWT.ESC) {
            m_text.setText("");
            str = "";
        }

        Point backup = null;

        //        if (IS_OS_WINDOWS) {
        //            Rectangle bounds = m_viewer.getTree().getParent().getShell().getBounds();
        //            // Bug 2809 -
        //            // on windows the search is much slower if the cursor is within the KNIME window.
        //            // so we just set it somewhere outside and restore it afterwards
        //            backup = Display.getCurrent().getCursorLocation();
        //            Display.getCurrent().setCursorLocation(new Point(bounds.x - 2, bounds.y - 2));
        //        }
        //
        //        m_viewer.setRedraw(false);
        //        try {
        //            if (str.length() == 0) {
        //                m_viewer.collapseAll();
        //                shouldExpand = false;
        //                update = true;
        //            }
        m_filter.setFilterString(str);
        for (IFilterableTreeViewer v : m_viewer) {
            v.setFilterIcon(str != null && !str.isEmpty());
            v.refresh();
        }
        //
        //            if (update) {
        //                if (shouldExpand) {
        //                    m_viewer.expandAll();
        //                }
        //            }
        //        } finally {
        //            if (backup != null) {
        //                Display.getCurrent().setCursorLocation(backup);
        //            }
        //            m_viewer.getControl().setRedraw(true);
        //        }

    }

    public void clearSearch() {
        keyReleased(SWT.ESC);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int computeWidth(final Control control) {
        return Math.max(super.computeWidth(control), 150);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void keyPressed(final KeyEvent e) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void keyReleased(final KeyEvent e) {
        keyReleased(e.character);
    }

}
