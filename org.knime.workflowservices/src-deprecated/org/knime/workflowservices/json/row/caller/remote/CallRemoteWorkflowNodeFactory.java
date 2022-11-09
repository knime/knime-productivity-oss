/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.com
 * email: contact@knime.com
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Feb 19, 2015 by wiswedel
 */
package org.knime.workflowservices.json.row.caller.remote;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 *
 * @author wiswedel
 * @deprecated use {@link CallWorkflowRowBased3NodeFactory}, it unifies local and remote row-based execution
 */
@Deprecated(since = "4.7")
public final class CallRemoteWorkflowNodeFactory extends NodeFactory<CallRemoteWorkflowNodeModel> {
    /**
     * {@inheritDoc}
     */
    @Override
    public CallRemoteWorkflowNodeModel createNodeModel() {
        return new CallRemoteWorkflowNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<CallRemoteWorkflowNodeModel> createNodeView(final int viewIndex, final CallRemoteWorkflowNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new CallRemoteWorkflowNodeDialogPane();
    }

}
