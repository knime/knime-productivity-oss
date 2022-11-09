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
 *   Created on Nov 13, 2020 by wiswedel
 */
package org.knime.workflowservices.json.table.caller;

import org.apache.commons.lang3.StringUtils;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObject;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Model for the deprecated Call Workflow Table node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @deprecated
 */
@Deprecated
final class Pre43CallWorkflowTableNodeModel extends AbstractCallWorkflowTableNodeModel { // NOSONAR

    Pre43CallWorkflowTableNodeModel() {
        super(ConnectionInformationPortObject.TYPE_OPTIONAL);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotEmpty(m_configuration.getWorkflowPath()), "No workflow path provided");

        var currentWfm = NodeContext.getContext().getWorkflowManager();
        var connectionSpec = inSpecs[0];

        m_serverConnection = ServerConnectionUtil.getConnection(connectionSpec, currentWfm);
        return new PortObjectSpec[]{null};
    }

    @Override
    protected BufferedDataTable getInputTable(final PortObject[] inObjects) {
        return (BufferedDataTable)inObjects[1];
    }

}
