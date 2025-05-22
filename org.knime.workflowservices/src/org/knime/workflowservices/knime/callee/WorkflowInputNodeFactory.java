/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   20.04.2021 (jl): created
 */
package org.knime.workflowservices.knime.callee;

import java.io.IOException;
import java.util.Optional;

import org.apache.xmlbeans.XmlException;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortType;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.xml.sax.SAXException;

/**
 * Node factory for Workflow Service Input.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction") // webui is not API yet
public final class WorkflowInputNodeFactory extends ConfigurableNodeFactory<WorkflowInputNodeModel>
    implements NodeDialogFactory {

    private static final String INPUT_PORT_GROUP = "Default Input";

    static final String OUTPUT_PORT_GROUP = "Callee Workflow Input";

    private static final String FULL_DESCRIPTION = """
            <p>
            Receives a table or any other port object from a KNIME workflow that
            calls this workflow using the <i>Call Workflow Service</i> node.
            </p>
            """;

    private static final WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder() //
        .name("Workflow Input") //
        .icon("service-in.png") //
        .shortDescription("Receives an input from another KNIME workflow that executes this workflow using the "
            + "Call Workflow Service node.") //
        .fullDescription(FULL_DESCRIPTION) //
        .modelSettingsClass(WorkflowInputSettings.class) //
        .addInputPort(INPUT_PORT_GROUP, PortObject.TYPE_OPTIONAL, //
            """
                In case no input is provided by the calling workflow, the provided input is used instead.
                This is also helpful to configure the downstream nodes of the <i>Workflow Input</i> node.
                """) //
        .addOutputPort(OUTPUT_PORT_GROUP, PortObject.TYPE, //
            """
                Input provided by a workflow calling this workflow using the <i>Call Workflow Service</i> node.
                The port can be configured to match the type of data that is to be received by this node.
                """) //
        .nodeType(NodeType.Container) //
        .sinceVersion(5, 5, 0) //
        .build();

    private final PortsConfigurationBuilder m_portsConfigurationBuilder;

    /**
     * Default constructor using the default port type.
     */
    public WorkflowInputNodeFactory() {
        this(WorkflowBoundaryConfiguration.DEFAULT_PORT_TYPE);
    }

    /**
     * Constructor with custom port type.
     *
     * @param type custom port type to use
     */
    public WorkflowInputNodeFactory(final PortType type) {
        m_portsConfigurationBuilder = new PortsConfigurationBuilder();
        m_portsConfigurationBuilder.addExchangeablePortGroup(OUTPUT_PORT_GROUP, type,
            WorkflowBoundaryConfiguration.ALL_PORT_TYPES);
    }

    @Override
    protected NodeDescription createNodeDescription() throws SAXException, IOException, XmlException {
        return WebUINodeFactory.createNodeDescription(CONFIG);
    }

    @Override
    public WorkflowInputNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new WorkflowInputNodeModel(creationConfig.getPortConfig().orElseThrow());
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        return Optional.of(m_portsConfigurationBuilder);
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    protected boolean hasDialog() {
        return true;
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeView<WorkflowInputNodeModel> createNodeView(final int viewIndex,
        final WorkflowInputNodeModel nodeModel) {
        throw new UnsupportedOperationException("This node has no views.");
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, WorkflowInputSettings.class);
    }

}
