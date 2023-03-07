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
package org.knime.workflowservices.json.row.caller3;

import java.util.Optional;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObject;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class CallWorkflowRowBased3NodeFactory extends ConfigurableNodeFactory<CallWorkflowRowBased3NodeModel> {

    static final String CONNECTION_INPUT_PORT_GRP_NAME = "File System Connection";

    static final String INPUT_PORT_GRP_NAME = "Input Port";

    static final String OUTPUT_PORT_GRP_NAME = "Output Port";

    /**
     * @param configuration provides whether a file system connector is present
     * @return if the connector is present, the data is input at port offset 1, otherwise 0
     */
    static int getDataPortIndex(final CallWorkflowConnectionConfiguration configuration) {
        return configuration.isConnectorPresent() ? 1 : 0;
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<CallWorkflowRowBased3NodeModel> createNodeView(final int viewIndex,
        final CallWorkflowRowBased3NodeModel nodeModel) {
        return null;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME, FileSystemPortObject.TYPE,
            FileSystemPortObject.TYPE,
            PortTypeRegistry.getInstance().getPortType(AbstractHubAuthenticationPortObject.class));
        b.addFixedInputPortGroup(INPUT_PORT_GRP_NAME, BufferedDataTable.TYPE);
        b.addFixedOutputPortGroup(OUTPUT_PORT_GRP_NAME, BufferedDataTable.TYPE);
        return Optional.of(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CallWorkflowRowBased3NodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflowRowBased3NodeModel(creationConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflowRowBased3NodeDialog(new CallWorkflowRowBased3Configuration(creationConfig));
    }

}
