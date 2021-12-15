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
 *   Created on May 24, 2018 by Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
package org.knime.workflowservices.json.caller;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.productivity.callworkflow.table.AbstractCallWorkflowTableNodeModel;
import org.knime.productivity.callworkflow.table.Pre43CallWorkflowTableNodeDialogPane;
import org.knime.productivity.callworkflow.table.Pre43CallWorkflowTableNodeModel;

/**
 * Factory for the Call Workflow node.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
public final class CallWorkflowTableNodeFactory extends NodeFactory<AbstractCallWorkflowTableNodeModel> {

    @Override
    public AbstractCallWorkflowTableNodeModel createNodeModel() {
        return new Pre43CallWorkflowTableNodeModel();
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<AbstractCallWorkflowTableNodeModel> createNodeView(final int viewIndex,
        final AbstractCallWorkflowTableNodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return new Pre43CallWorkflowTableNodeDialogPane();
    }

}
