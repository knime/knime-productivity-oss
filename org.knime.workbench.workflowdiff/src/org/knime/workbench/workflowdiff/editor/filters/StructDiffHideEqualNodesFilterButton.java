/*
 * ------------------------------------------------------------------------
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
package org.knime.workbench.workflowdiff.editor.filters;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

/**
 * Toolbar contribution with a filter for the view.
 *
 * @author Peter Ohl, KNIME AG, Zurich, Switzerland
 */
public class StructDiffHideEqualNodesFilterButton extends ControlContribution {

    private final StructureDiffFilter m_filter;
    
    private final Viewer[] m_viewer;

    /**
     * Creates the contribution item. The button that clears the filter text field.
     * @param textField to clear on click
     *
     */
    public StructDiffHideEqualNodesFilterButton(final StructureDiffFilter filter, Viewer...viewers) {
        super("org.knime.workflow.diff.structdiffhideequalnodesfilterbutton");
        m_filter = filter;
        if (viewers == null) {
        	m_viewer = new Viewer[0];
        } else {
        	m_viewer = viewers;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Control createControl(final Composite parent) {
        final Button btn = new Button(parent, SWT.TOGGLE);
        btn.setImage(ImageRepository.getIconImage(SharedImages.ButtonHideEqualNodes));
        btn.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        btn.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                m_filter.setHideEqualNodes(btn.getSelection());
                for (Viewer v : m_viewer) {
                	v.refresh();
                }
            }
            @Override
            public void widgetDefaultSelected(final SelectionEvent e) {
                //huh?
            }
        });
        btn.setToolTipText("Hide nodes with equal settings.");
        return btn;
    }

}
