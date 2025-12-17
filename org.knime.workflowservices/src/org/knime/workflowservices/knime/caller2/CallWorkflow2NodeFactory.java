package org.knime.workflowservices.knime.caller2;

import static org.knime.node.impl.description.PortDescription.dynamicPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortType;
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
import org.knime.workflowservices.CallWorkflowParameters.WorkflowExecutionConnectorProvider;
import org.knime.workflowservices.connection.AbstractHubAuthenticationPortObject;
import org.knime.workflowservices.knime.caller2.CallWorkflow2NodeParameters.UpdatePortsOnApplyModifier;

/**
 * Node factory for the Call Workflow Service node.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 * @author Paul Baernreuther, KNIME GmbH, Germany
 * @author AI Migration Pipeline v1.2
 */
@SuppressWarnings("restriction")
public class CallWorkflow2NodeFactory extends ConfigurableNodeFactory<CallWorkflow2NodeModel>
    implements NodeDialogFactory, KaiNodeInterfaceFactory {

    static final String CONNECTION_INPUT_PORT_GRP_NAME = "File System Connection";

    static final String INPUT_PORT_GROUP = "Inputs";

    static final String OUTPUT_PORT_GROUP = "Outputs";

    @Override
    public CallWorkflow2NodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflow2NodeModel(creationConfig);
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        Predicate<PortType> allPorts = p -> true;
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME, //
            FileSystemPortObject.TYPE, //
            AbstractHubAuthenticationPortObject.TYPE);
        // non-interactive means this cannot be controlled by the user via the user interface. Instead, the node dialog
        // updates the input and output ports according to the selected callee workflow.
        b.addNonInteractiveExtendableInputPortGroup(INPUT_PORT_GROUP, allPorts);
        b.addNonInteractiveExtendableOutputPortGroup(OUTPUT_PORT_GROUP, allPorts);
        return Optional.of(b);
    }

    @Override
    public int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<CallWorkflow2NodeModel> createNodeView(final int viewIndex,
        final CallWorkflow2NodeModel nodeModel) {
        return null;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    private static final String NODE_NAME = "Call Workflow Service";

    private static final String NODE_ICON = "./callworkflow.png";

    private static final String SHORT_DESCRIPTION = """
            Executes another workflow and obtains the results for further processing.
            """;

    private static final String FULL_DESCRIPTION = """
            <p> Calls another workflow and obtains the results for further processing in this workflow. The workflow
                can receive inputs via <i>Workflow Input</i> nodes and return outputs using <i>Workflow Output</i>
                nodes. </p> <p> Each <i>Workflow Input</i> node in the workflow to be called will create an input port
                on this node when finishing the configuration of the Call Workflow Service node. Similarly, each
                <i>Workflow Output</i> node will create an output port on the node. </p> <p> The called workflow can be
                local or remote. If the workflow is remote, e.g., on the KNIME Hub, the execution will also be remote.
                When the workflow is local, the execution will be performed on the machine on which the Analytics
                Platform is running. In contrast to the <i>Workflow Executor</i> node, this node does not require the
                workflow to be read using a <i>Workflow Reader</i> node. </p> <p> The difference between this node and
                the <i>Call Workflow (Table Based)</i> node is the set of supported workflow input and output nodes.
                This node supports the <i>Workflow Input</i> and <i>Workflow Output</i> nodes, which support arbitrary
                port types and are more efficient than the various <i>Container Input</i> and <i>Container Output</i>
                nodes. The container input and output nodes on the other hand expect and produce data in a format that
                can easily be produced by third-party software, whereas <i>Workflow Input</i> and <i>Workflow Output</i>
                nodes are designed exclusively to be used by other KNIME workflows. </p> <p> To define which <i>Workflow
                Input</i> node receives data from which of this node's input ports, each input node defines a
                <b>parameter identifier.</b> The parameter identifier is supposed to be unique, but it might happen that
                a workflow has multiple input nodes defining the same parameter name. In this case, KNIME will make the
                parameter names unique by appending the input node's node ID to the parameter name, e.g., "input-table"
                becomes "input-table-7". </p>
            """;

    private static final List<PortDescription> INPUT_PORTS =
        List.of(dynamicPort("File System Connection", "Connection", """
                An optional connection to a file system that contains the callee workflow or a hub instance that
                provides deployed workflows. The callee workflow will be executed where it is located, e.g., on the
                KNIME Hub. If a Hub Authenticator is connected, only workflows that have previously been deployed can be
                executed. If a Space Connector or Mountpoint connector is connected, any workflow can be selected and
                executed.
                """), dynamicPort("Inputs", "Workflow inputs", """
                One input port for each <i>Workflow Input</i> node in the workflow to be executed. The ports are
                automatically created when finishing the configuration of the node.
                """));

    private static final List<PortDescription> OUTPUT_PORTS = List.of(dynamicPort("Outputs", "Workflow outputs", """
            One output port for each <i>Workflow Output</i> node in the workflow to be executed. The ports are
            automatically created when finishing the configuration of the node.
            """));

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, CallWorkflow2NodeParameters.class,
            new UpdatePortsOnApplyModifier(), WorkflowExecutionConnectorProvider::terminateAndClearAllRunningThreads);
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
            CallWorkflow2NodeParameters.class, //
            null, //
            NodeType.Other, //
            List.of(), //
            null //
        );
    }

    @Override
    public KaiNodeInterface createKaiNodeInterface() {
        return new DefaultKaiNodeInterface(Map.of(SettingsType.MODEL, CallWorkflow2NodeParameters.class));
    }

}
