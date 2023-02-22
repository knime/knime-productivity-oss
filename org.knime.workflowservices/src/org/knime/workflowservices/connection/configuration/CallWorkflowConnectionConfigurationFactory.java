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
 *   Created on 22 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import java.util.Optional;

import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortType;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class CallWorkflowConnectionConfigurationFactory {

    /**
     * Determines the connection type (local, remote server, remote hub workflow, remote hub deployment) from the node
     * ports.
     *
     * @param creationConfig
     * @param inputPortGroupName
     * @return a configuration matching the call workflow connection type
     */
    public static LocalCallWorkflowConnectionConfiguration<?> create(final NodeCreationConfiguration creationConfig,
        final String inputPortGroupName) {
        // hub authenticator leads to a deployment connection configuration
        var optPortConfig = creationConfig.getPortConfig();
        if (optPortConfig.isEmpty()) {
            throw new IllegalStateException("No port configuration passed to the Call Workflow configuration.");
        }

        var portConfig = optPortConfig.get();
        Optional<Integer> connectorPortIndex = ConnectorInvocationTarget.getConnectorPortIndex(inputPortGroupName, portConfig);
        Optional<PortType> inputPort = connectorPortIndex.map(idx -> portConfig.getInputPorts()[idx]);

        // if no connector is present, the file system invocation target is used. The workflow chooser in the target looks
        // at the workflow context
        InvocationTarget target;
        if(inputPort.isPresent() && AbstractHub
                ) {
            var fsTarget = new FileSystemInvocationTarget(creationConfig, inputPortGroupName);
            fsTarget.getWorkflowChooserModel().getLocation().getFSType();
        }
        return null;
    }



}
