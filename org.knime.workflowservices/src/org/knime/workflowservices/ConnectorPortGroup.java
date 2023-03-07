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
 *   Created on 6 Mar 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.util.Optional;

import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;

/**
 * Ports configuration and connector port group name are often passed together, e.g., to initialize a workflow chooser.
 *
 * @param nodeConfiguration the node's port configuration. Not null.
 * @param connectorPortGroupName The name of the port group that may or may not contain the input port that provides
 *            access to the invocation target. It may be absent if access is provided implicitly from the workflow
 *            context, e.g., a workflow in a hub space will behave as if a space connector to that space is present.
 *
 * @noreference Non-public API. This type is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public record ConnectorPortGroup(NodeCreationConfiguration nodeConfiguration, String connectorPortGroupName) {

    /**
     * @param nodeConfiguration Must not be null.
     * @param connectorPortGroupName the name of the port group that holds the connector port, e.g., a Hub
     *            Authentication port or a File System Connection port. Must not be null.
     */
    public ConnectorPortGroup {
        CheckUtils.checkNotNull(nodeConfiguration, "The node creation configuration must not be null.");
        CheckUtils.checkNotNull(connectorPortGroupName,
            "The identifier of the file system connector input port group must not be null.");
        var portConfig = nodeConfiguration.getPortConfig();
        if (portConfig.isEmpty()) {
            throw new IllegalStateException("No port configuration present.");
        }
        // either empty port group or exactly one port
        int[] connectorPorts = portConfig.get().getInputPortLocation().get(connectorPortGroupName);
        CheckUtils.check(connectorPorts == null || connectorPorts.length <= 1, IllegalStateException::new,
            () -> "Expected at most one connector port");
    }

    /**
     * @return the position of the port containing the connector port (e.g., hub authenticator or file system port) or
     *         empty if the port group does not contain a port.
     * @throws IllegalStateException if the specified port group contains more than one port.
     */
    public Optional<Integer> getConnectorPortIndex() {
        int[] connectorPortLocation = portsConfiguration().getInputPortLocation().get(connectorPortGroupName);

        if (connectorPortLocation != null && connectorPortLocation.length > 0) {
            return Optional.of(connectorPortLocation[0]);
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return the connector port type from the node configuration
     */
    public Optional<PortType> connectorPortType() {
        return getConnectorPortIndex().map(idx -> portsConfiguration().getInputPorts()[idx]);
    }

    /**
     * @return the configured port groups on a node
     */
    public PortsConfiguration portsConfiguration() {
        return nodeConfiguration.getPortConfig().orElse(null);
    }
}
