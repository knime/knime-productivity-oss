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
package org.knime.workflowservices.json.table.caller2;

import java.util.Optional;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObject;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.json.table.caller.AbstractCallWorkflowTableNodeModel;
import org.knime.workflowservices.json.table.caller.CallWorkflowTableNodeConfiguration;

/**
 * Factory for Call Workflow Table node, added in 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
public final class CallWorkflowTable2NodeFactory extends ConfigurableNodeFactory<AbstractCallWorkflowTableNodeModel> {

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
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME,//
            FileSystemPortObject.TYPE,//
            AbstractHubAuthenticationPortObject.TYPE);
        b.addFixedInputPortGroup(INPUT_PORT_GRP_NAME, BufferedDataTable.TYPE);
        b.addFixedOutputPortGroup(OUTPUT_PORT_GRP_NAME, BufferedDataTable.TYPE);
        return Optional.of(b);
    }

    @Override
    public AbstractCallWorkflowTableNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflowTable2NodeModel(creationConfig);
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
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflowTable2NodeDialog(
            new CallWorkflowTableNodeConfiguration(creationConfig, CONNECTION_INPUT_PORT_GRP_NAME));
    }

}
