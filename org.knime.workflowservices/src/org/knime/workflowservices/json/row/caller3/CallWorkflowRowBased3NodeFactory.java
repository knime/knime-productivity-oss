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

import static org.knime.node.impl.description.PortDescription.dynamicPort;
import static org.knime.node.impl.description.PortDescription.fixedPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultKaiNodeInterface;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterface;
import org.knime.core.webui.node.dialog.kai.KaiNodeInterfaceFactory;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.node.impl.description.DefaultNodeDescriptionUtil;
import org.knime.node.impl.description.PortDescription;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObject;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public final class CallWorkflowRowBased3NodeFactory extends ConfigurableNodeFactory<CallWorkflowRowBased3NodeModel> implements NodeDialogFactory, KaiNodeInterfaceFactory {

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
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME,//
            FileSystemPortObject.TYPE,//
            AbstractHubAuthenticationPortObject.TYPE);
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
    private static final String NODE_NAME = "Call Workflow (Row Based)";
    private static final String NODE_ICON = "./callworkflow.png";
    private static final String SHORT_DESCRIPTION = """
            Calls a remote or local workflow for each row in the input table, passing a JSON object and appending
                results as new columns.
            """;
    private static final String FULL_DESCRIPTION = """
            This node passes JSON objects to a workflow, executes the workflow, and fetches the returned JSON
                objects. This happens once for each row in the input table, appending the fetched results for the row as
                new cells to the row. <br /><br /> <b>Sending data.</b> The called workflow can receive data from this
                node via the Container Input nodes, e.g., JSON, Row, or Table, which all expect a JSON object but make
                different assumptions on the structure of the object. For instance, Container Input (JSON) accepts any
                JSON object, while Container Input (Row) expects a JSON object where each key corresponds to a column
                name and the associated value denotes the according cell content. <br /> What is passed to a specific
                Container Input node affects the execution of the called workflow. There are three options: <ul>
                <li><i>From column:</i> pass the JSON contained in a selected column of the current input row. The
                called workflow is executed for each row.</li> <li><i>Use custom JSON:</i> pass static JSON. The called
                workflow is executed only once, the result is reused for all subsequent rows.</li> <li><i>Use
                default:</i> send nothing, causing the default JSON object defined by the according Container Input node
                to be used. The called workflow is executed once, the result is reused for all subsequent rows. Note
                that if the called workflow has been saved with one of the output nodes in executed state, the return
                value for that output value is the json value "null". </li> </ul> <br /><br /> <b>Receiving data.</b>
                The called workflow can send back data via Container Output nodes (Row, Table, or JSON). Each Container
                Output node will result in a column being appended to the output table. <br /><br /> <b>Concurrent
                execution.</b> Note that if the called workflow is local, concurrent calls to it will not be processed
                in parallel, i.e., each call we be executed sequentially. If the called workflow is remote, each call
                will result in a new job which can be executed in parallel with other jobs.
            """;
    private static final List<PortDescription> INPUT_PORTS = List.of(
            dynamicPort("File System Connection", "Connection", """
                An optional connection to a file system that contains the callee workflow or a hub instance that
                provides deployed workflows. The callee workflow will be executed where it is located, e.g., on the
                KNIME Hub. If a Hub Authenticator is connected, only workflows that have previously been deployed can be
                executed. If a Space Connector or Mountpoint connector is connected, any workflow can be selected and
                executed.
                """),
            fixedPort("Input table", """
                The callee workflow is invoked for each row in this table. Cells in JSON columns can be sent to specific
                input nodes in the callee workflow.
                """)
    );
    private static final List<PortDescription> OUTPUT_PORTS = List.of(
            fixedPort("Output", """
                Input table with the results returned by the callee workflow appended as new cells to each row.
                """)
    );


    /**
     * @since 5.10
     */
    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    /**
     * @since 5.10
     */
    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, CallWorkflowRowBased3NodeParameters.class);
    }

    @Override
    public NodeDescription createNodeDescription() {
        return DefaultNodeDescriptionUtil.createNodeDescription( //
            NODE_NAME, //
            NODE_ICON, //
            INPUT_PORTS, //
            OUTPUT_PORTS, //
            SHORT_DESCRIPTION, //
            FULL_DESCRIPTION, //
            List.of(), //
            CallWorkflowRowBased3NodeParameters.class, //
            null, //
            NodeType.Other, //
            List.of(), //
            null //
        );
    }

    /**
     * @since 5.10
     */
    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, CallWorkflowRowBased3NodeParameters.class));
    }

}
