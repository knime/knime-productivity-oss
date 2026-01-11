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
import org.knime.workflowservices.json.table.caller.AbstractCallWorkflowTableNodeModel;

/**
 * Factory for Call Workflow Table node, added in 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public final class CallWorkflowTable2NodeFactory extends ConfigurableNodeFactory<AbstractCallWorkflowTableNodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

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
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME, //
            FileSystemPortObject.TYPE, //
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

    private static final String NODE_NAME = "Call Workflow (Table Based)";

    private static final String NODE_ICON = "./callworkflow.png";

    private static final String SHORT_DESCRIPTION = """
            Calls workflows
            """;

    private static final String FULL_DESCRIPTION = """
            This node can be used to call other workflows that reside either locally or remotely on the KNIME Hub.
                The called workflow may contain one or multiple of the following nodes to receive data from the workflow
                containing this node: <ul> <li>Container Input (Table)</li> <li>Container Input (Variable)</li>
                <li>Container Input (Credentials)</li> </ul> It may also contain a <i>Container Output (Table)</i> node
                to define the interface between this workflow and the called workflow in the following way: <br /> <br
                /> <b>Send Table. </b> The data table provided at the input of the <i>Call Workflow (Table Based)</i>
                node can be sent to a <i>Container Input (Table)</i> node in the called workflow via the input parameter
                defined in the <i>Container Input (Table)</i> node <br /> <br /> <b>Receive Table.</b> After the
                execution of the called workflow, a data table can be received from a <i>Container Output (Table)</i>
                node in the called workflow via the output parameter defined in the <i>Container Output (Table)</i>
                node. The received data table will be made available at the output port of the <i>Call Workflow (Table
                Based)</i> node <br /> <br /> <b>Send Flow Variables.</b> Certain flow variables available in the
                <i>Call Workflow (Table Based)</i> node can be sent to a <i>Container Input (Variable)</i> node in the
                called workflow. Currently, neither boolean, long, nor list or set flow variables are supported and will
                be ignored without warning. The supported types are mostly converted to string flow variables (e.g.,
                path or URI flow variables), except for integer flow variables and non-NaN double flow variables. Note
                that the <i>Call Workflow Service</i> node supports all flow variable types. <br /> <br /> <b>Send Flow
                Credentials.</b> All flow credentials available in the <i>Call Workflow (Table Based)</i> node can be
                sent to a <i>Container Input (Credential)</i> node in the called workflow via the credential-input
                defined in the <i>Container Input (Credential)</i> node<br /> <br /> To call workflows on a KNIME Hub, a
                <i>KNIME Space Connector</i> node must be connected to this node. You can add a File System Connection
                port by clicking on the three dots on the node. <br />
            """;

    private static final List<PortDescription> INPUT_PORTS =
        List.of(dynamicPort(CONNECTION_INPUT_PORT_GRP_NAME, "Connection", """
                An optional connection to a file system that contains the callee workflow or a hub instance that
                provides deployed workflows. The callee workflow will be executed where it is located, e.g., on the
                KNIME Hub. If a Hub Authenticator is connected, only workflows that have previously been deployed can be
                executed. If a Space Connector or Mountpoint connector is connected, any workflow can be selected and
                executed.
                """), fixedPort("Input table", """
                The data to send to the callee workflow.
                """));

    private static final List<PortDescription> OUTPUT_PORTS = List.of(fixedPort("Output", """
            Data returned by the callee workflow.
            """));

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, CallWorkflowTable2NodeParameters.class);
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
            CallWorkflowTable2NodeParameters.class, //
            null, //
            NodeType.Other, //
            List.of(), //
            null //
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, CallWorkflowTable2NodeParameters.class));
    }

}
