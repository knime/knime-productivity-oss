/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
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
 *   03.11.2014 (ohl): created
 */
package org.knime.workbench.workflowdiff.editor.filters;

import java.util.Locale;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

/**
 * 
 * @author ohl
 */
public class NodeDiffFilter extends ViewerFilter {

	private String m_match;

	public void setFilterString(final String s) {
		if (s.isEmpty()) {
			m_match = null;
		} else {
			m_match = s.toLowerCase(Locale.US);
		}
	}

	protected String getFilterString() {
		return m_match;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean select(final Viewer viewer, final Object parentElement, final Object element) {
		if (element == null) {
			return false;
		} else if (m_match == null) {
			return true;
		} else {
			if (element instanceof IHierMatchableItem) {
				return matchFullHierarchy(viewer, (IHierMatchableItem) element);
			} else {
				return element.toString().toLowerCase(Locale.US).contains(m_match);
			}
		}

	}

	protected boolean matchFullHierarchy(final Viewer viewer, final IHierMatchableItem item) {
		if (match(viewer, item)) {
			return true;
		}
		if (matchDown(viewer, item)) {
			return true;
		}
		if (matchUp(viewer, item)) {
			return true;
		}
		return false;
	}

	protected boolean matchDown(final Viewer viewer, final IHierMatchableItem item) {
		for (IHierMatchableItem child : item.getMatchChildren()) {
			if (match(viewer, child)) {
				return true;
			}
			if (matchDown(viewer, child)) {
				return true;
			}
		}
		return false;
	}

	protected boolean matchUp(final Viewer viewer, final IHierMatchableItem item) {
		IHierMatchableItem p = item.getMatchParent();
		while (p != null) {
			if (match(viewer, p)) {
				return true;
			}
			p = p.getMatchParent();
		}
		return false;
	}

	protected boolean match(final Viewer viewer, final IHierMatchableItem item) {
		if (viewer instanceof IFilterableTreeViewer) {
			int cols = ((IFilterableTreeViewer) viewer).getMatchNumOfColumns();
			for (int i = 0; i < cols; i++) {
				String text = ((IFilterableTreeViewer) viewer).getMatchLabel(item, i);
				if (text != null && text.toLowerCase(Locale.US).contains(m_match)) {
					return true;
				}
			}
		} else {
			String text = item.toString();
			if (text != null && text.toLowerCase(Locale.US).contains(m_match)) {
				return true;
			}
		}
		return false;
	}
}
