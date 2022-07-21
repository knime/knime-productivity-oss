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
 *   Created on Jun 20, 2017 by wiswedel
 */
package org.knime.workflowservices;

import java.util.Map;

import org.knime.core.node.MapNodeFactoryClassMapper;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeModel;
import org.knime.workflowservices.json.row.caller.local.CallLocalWorkflowNodeFactory;

/**
 * The "Call Local Workflow (Row Based)" used to be in a different plug-in and this mapper resolves the old path stored
 * in (old) workflows.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class WorkflowServicesFactoryClassMapper extends MapNodeFactoryClassMapper {

    @Override
    protected Map<String, Class<? extends NodeFactory<? extends NodeModel>>> getMapInternal() {
        return Map.of(//
            "com.knime.explorer.nodes.callworkflow.local.CallLocalWorkflowNodeFactory",
            CallLocalWorkflowNodeFactory.class,
            "org.knime.explorer.nodes.callworkflow.local.CallLocalWorkflowNodeFactory",
            CallLocalWorkflowNodeFactory.class);
    }
}
