/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 */
package org.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.knime.workbench.workflowdiff.editor.filters.IHierMatchableItem;

/**
 * Using our own class to be able to distinguish from others and to instantiate our own viewer.
 *
 * @author ohl
 */
public class FlowDiffNode extends DiffNode implements IHierMatchableItem {

	private boolean fDirty = false;

	private ITypedElement fLastId;

	private String fLastName;

	public FlowDiffNode(final IDiffContainer parent, final int description, final ITypedElement ancestor,
			final ITypedElement left, final ITypedElement right) {
		super(parent, description, ancestor, left, right);
	}

	void clearDirty() {
		fDirty = false;
	}

	@Override
	public String getName() {
		if (fLastName == null) {
			fLastName = super.getName();
		}
		if (fDirty) {
			return '<' + fLastName + '>';
		}
		return fLastName;
	}

	@Override
	public ITypedElement getId() {
		ITypedElement id = super.getId();
		if (id == null) {
			return fLastId;
		}
		fLastId = id;
		return id;
	}

	@Override
	public IHierMatchableItem[] getMatchChildren() {
		IDiffElement[] children = getChildren();
		IHierMatchableItem[] result = new IHierMatchableItem[children.length];
		for (int i = 0; i < children.length; i++) {
			result[i] = (IHierMatchableItem) children[i];
		}
		return result;
	}

	@Override
	public IHierMatchableItem getMatchParent() {
		IDiffElement p = getParent();
		if (p instanceof IHierMatchableItem) {
			return (IHierMatchableItem) p;
		}
		return null;
	}

}