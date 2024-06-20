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
 *   Created on Nov 14, 2020 by wiswedel
 */
package org.knime.workflowservices.json.table.caller2;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObjectSpec;
import org.knime.workflowservices.connection.util.ConnectionUtil;
import org.knime.workflowservices.json.table.caller.AbstractCallWorkflowTableNodeModel;
import org.knime.workflowservices.json.table.caller.CallWorkflowTableNodeConfiguration;

/**
 * Model for Call Workflow Table node, added in 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class CallWorkflowTable2NodeModel extends AbstractCallWorkflowTableNodeModel {

    CallWorkflowTable2NodeModel(final NodeCreationConfiguration nodeCreationConfiguration) {
        super(getPortsConfig(nodeCreationConfiguration).getInputPorts(), //
            getPortsConfig(nodeCreationConfiguration).getOutputPorts(), //
            new CallWorkflowTableNodeConfiguration(nodeCreationConfiguration,
                CallWorkflowTable2NodeFactory.CONNECTION_INPUT_PORT_GRP_NAME));
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (inSpecs.length > 0 && inSpecs[0] instanceof AbstractHubAuthenticationPortObjectSpec hubAuthPortObjectSpec) {
            m_configuration.setHubAuthentication(hubAuthPortObjectSpec);
        } else {
            m_configuration.configureCalleeModel(inSpecs);
            ConnectionUtil.validateConfiguration(m_configuration);
        }
        return new PortObjectSpec[]{null};
    }

    private static PortsConfiguration getPortsConfig(final NodeCreationConfiguration creationConfig) {
        return creationConfig.getPortConfig().orElseThrow(IllegalStateException::new);
    }

    @Override
    protected BufferedDataTable getInputTable(final PortObject[] inObjects) {
        final int dataPortIdx = CallWorkflowTable2NodeFactory.getDataPortIndex(m_configuration);
        return (BufferedDataTable)inObjects[dataPortIdx];
    }

}
