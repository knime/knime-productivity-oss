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
 * History
 *   03.11.2014 (ohl): created
 */
package com.knime.workbench.workflowdiff.editor;

import java.util.Locale;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import com.knime.workbench.workflowdiff.editor.NodeSettingsTreeViewer.SettingsItem;

/**
 *
 * @author ohl
 */
public class NodeSettingsFilter extends ViewerFilter {

    private String m_match;

    public void setFilterString(final String s) {
        if (s.isEmpty()) {
            m_match = null;
        } else {
            m_match = s.toLowerCase(Locale.US);
        }
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
            if (element instanceof SettingsItem) {
                return matchFullHierarchy((SettingsItem)element);
            } else {
                return element.toString().toLowerCase(Locale.US).contains(m_match);
            }
        }

    }

    private boolean matchFullHierarchy(final SettingsItem item) {
        if (match(item)) {
            return true;
        }
        if (matchDown(item)) {
            return true;
        }
        if (matchUp(item)) {
            return true;
        }
        return false;
    }

    private boolean matchDown(final SettingsItem item) {
        for (SettingsItem child : item.getChildren()) {
            if (match(child)) {
                return true;
            }
            if (matchDown(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchUp(final SettingsItem item) {
        SettingsItem p = item.getParent();
        while (p != null) {
            if (match(p)) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }
    private boolean match(final SettingsItem item) {
        for (int i = 0; i < 3; i++) {
            if (item.getText(i) != null && item.getText(i).toLowerCase(Locale.US).contains(m_match)) {
                return true;
            }
        }
        return false;
    }
}
