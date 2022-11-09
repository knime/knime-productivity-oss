package org.knime.workflowservices.knime.caller;

import java.util.Optional;
import java.util.function.Predicate;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.workflowservices.knime.caller2.CallWorkflow2NodeFactory;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 * @deprecated use {@link CallWorkflow2NodeFactory}
 */
@Deprecated(since = "4.7.0")
public class CallWorkflowNodeFactory extends ConfigurableNodeFactory<CallWorkflowNodeModel> {

    static final String INPUT_PORT_GROUP = "Inputs";

    static final String OUTPUT_PORT_GROUP = "Outputs";

    @Override
    public CallWorkflowNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        final var portConfig = creationConfig.getPortConfig().orElseThrow();
        return new CallWorkflowNodeModel(portConfig);
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        Predicate<PortType> allPorts = p -> true;
        b.addFixedInputPortGroup("FileSystem", FileSystemPortObject.TYPE_OPTIONAL);
        b.addExtendableInputPortGroup(INPUT_PORT_GROUP, allPorts);
        b.addExtendableOutputPortGroup(OUTPUT_PORT_GROUP, allPorts);
        return Optional.of(b);
    }

    @Override
    public boolean isPortConfigurableViaMenu() {
        return false;
    }

    @Override
    public NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return new CallWorkflowNodeDialog(creationConfig);
    }

    @Override
    public int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<CallWorkflowNodeModel> createNodeView(final int viewIndex, final CallWorkflowNodeModel nodeModel) {
        return null;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

}

