package org.knime.workflowservices.knime.caller2;

import java.util.Optional;
import java.util.function.Predicate;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.filehandling.core.port.FileSystemPortObject;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public class CallWorkflow2NodeFactory extends ConfigurableNodeFactory<CallWorkflow2NodeModel> {

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
        b.addOptionalInputPortGroup(CONNECTION_INPUT_PORT_GRP_NAME, FileSystemPortObject.TYPE);
        // non-interactive means this cannot be controlled by the user via the user interface. Instead, the node dialog
        // updates the input and output ports according to the selected callee workflow.
        b.addNonInteractiveExtendableInputPortGroup(INPUT_PORT_GROUP, allPorts);
        b.addNonInteractiveExtendableOutputPortGroup(OUTPUT_PORT_GROUP, allPorts);
        return Optional.of(b);
    }

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflow2NodeDialog(creationConfig);
    }

    @Override
    public int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<CallWorkflow2NodeModel> createNodeView(final int viewIndex, final CallWorkflow2NodeModel nodeModel) {
        return null;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

}

