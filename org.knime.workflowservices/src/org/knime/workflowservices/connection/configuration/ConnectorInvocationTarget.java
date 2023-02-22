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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import java.util.Optional;

import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.util.CheckUtils;

/**
 * An invocation target that can use a connector. This encapsulates the logic that decides which ports are merely
 * providing access to the invocation target and which ports carry data that needs to be sent to the invocation target.
 * For instance
 * <ul>
 * <li>If a file system connector is added to a call workflow node, that connector can be used to access a workflow on a
 * hub space or in a server repository.</li>
 * <li>If a workflow is located on the hub and opened for editing, that workflow context will affect the call workflow
 * nodes. They will add as if a space connector is connected, but since the connection is implicit, all ports carry
 * input for the invocation target in this case.</li>
 * <li>A call workflow node may use a hub authenticator port to access the available deployments. Both the
 * {@link FileSystemInvocationTarget} and the {@link DeploymentInvocationTarget} share the logic that identifies the
 * data ports but manage access to the invocation target in a different way.</li>
 * </ul>
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public abstract class ConnectorInvocationTarget implements InvocationTarget {

    final PortsConfiguration m_portsConfiguration;

    /**
     * The name of the port group that may or may not contain the input port that provides access to the invocation
     * target. It may be absent if access is provided implicitly from the workflow context, e.g., a workflow in a hub
     * space will behave as if a space connector to that space is present.
     */
    protected final String m_connectorPortGroupName;

    /**
     * @param creationConfig provides access to a node's ports
     * @param connectorPortGroupName the identifier of the port group that contains a single port that provides access to
     *            the invocation target, e.g., a file system connector port for workflows or a hub authenticator port
     *            for deployments
     */
    public ConnectorInvocationTarget(final NodeCreationConfiguration creationConfig, final String connectorPortGroupName) {
        CheckUtils.checkNotNull(connectorPortGroupName,
                "The identifier of the file system connector input port group must not be null.");

        var portConfig = creationConfig.getPortConfig();
        if (portConfig.isEmpty()) {
            throw new IllegalStateException("No port configuration passed to the Call Workflow configuration.");
        }

        m_portsConfiguration =  portConfig.get();
        m_connectorPortGroupName = connectorPortGroupName;
    }

    /**
     * @param fileSystemPortGroupName the name of the port group that contains the file system port
     * @param portsConfiguration the current port configuration of a node
     * @return
     */
    private static Optional<Integer> getConnectorPortIndex(final String fileSystemPortGroupName, final PortsConfiguration portsConfiguration) {
        if (fileSystemPortGroupName == null) {
            return Optional.empty();
        }
        int[] fileSystemPortLocation = portsConfiguration.getInputPortLocation().get(fileSystemPortGroupName);

        if (fileSystemPortLocation != null && fileSystemPortLocation.length > 0) {
            if (fileSystemPortLocation.length > 1) {
                throw new IllegalStateException("Coding error: More than one file system port connector present.");
            }
            return Optional.of(fileSystemPortLocation[0]);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Integer> getConnectorPortIndex() {
        // new nodes can be configured to have a file system connector port if necessary
        return getConnectorPortIndex(m_connectorPortGroupName, m_portsConfiguration);
    }

    /**
     * @return the ports configuration that might contain an input port that provides access to an invocation target
     *         location
     */
    public Optional<PortsConfiguration> getPortsConfiguration() {
        return Optional.ofNullable(m_portsConfiguration);
    }

}