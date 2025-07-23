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
 */
package org.knime.workflowservices.knime.callee;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.dialog.InputNode;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;
import org.knime.core.webui.node.dialog.defaultdialog.NodeParametersUtil;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.internal.WorkflowIOParameterNameValidation;
import org.knime.workflowservices.knime.util.CallWorkflowPayload;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
@SuppressWarnings("restriction") // webui is not API yet
final class WorkflowInputNodeModel extends AbstractPortObjectRepositoryNodeModel implements InputNode {

    public static final String DEFAULT_PARAM_NAME = "input-parameter";

    private WorkflowBoundaryConfiguration m_settings;

    private CallWorkflowPayload m_payload;

    WorkflowInputNodeModel(final PortsConfiguration creationConfig) {
        super(toOptional(creationConfig.getInputPorts()), creationConfig.getOutputPorts());
        m_settings = new WorkflowInputSettings();
    }

    /**
     * Get "optional counterpart" for the selected output type (this node's input is optional).
     *
     * @param types the type array (for our purposes always length 1)
     * @return new array with the same type (but optional)
     */
    private static PortType[] toOptional(final PortType[] types) {
        return Arrays.stream(types) //
                .map(p -> PortTypeRegistry.getInstance().getPortType(p.getPortObjectClass(), true)) //
                .toArray(PortType[]::new);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        if (m_payload != null) {
            return new PortObjectSpec[] {m_payload.getSpec()};
        }
        var spec = inSpecs[0];
        if (spec != null) {
            // node is connected to some input
            return new PortObjectSpec[] {spec};
        }
        throw new InvalidSettingsException("No data object set from calling workflow (via external API)");
    }

    /**
     * If external input data is present (as set by {@link #setInputData(ExternalNodeData)}), deserialize the contents
     * and provide to downstream nodes as a regular {@link PortObject}.
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        if (m_payload != null) {
            @SuppressWarnings("unchecked")
            var portObject = m_payload.onExecute(exec, variable -> {
                @SuppressWarnings("rawtypes")
                VariableType expectedType = variable.getVariableType(); // NOSONAR must be declared as raw type
                pushFlowVariable(variable.getName(), expectedType, variable.getValue(expectedType));
            }, this);
            return new PortObject[]{portObject};
        } else if (inObjects[0] != null) {
            return new PortObject[]{inObjects[0]};
        }
        throw new IllegalStateException("No data set, method should not have been called");
    }

    @Override
    public ExternalNodeData getInputData() {
        return m_settings.toExternalNodeData(getOutPortType(0), null);
    }

    @Override
    public void validateInputData(final ExternalNodeData inputData) throws InvalidSettingsException {
        URI resource = inputData.getResource();
        CheckUtils.checkSettingNotNull(resource, "Input data expected to provide a binary object (via URI)");
        CheckUtils.checkSetting(!Objects.equals(resource, ExternalNodeData.NO_URI_VALUE_YET),
            "No input data specified for Workflow Service Input");
    }

    /**
     * This is used by the caller workflow to a) initialize the subworkflow and retrieve information about the output
     * nodes and b) to set the actual data for execution
     */
    @Override
    public void setInputData(final ExternalNodeData inputData) throws InvalidSettingsException {
        var locationURI = inputData.getResource();
        CheckUtils.checkArgumentNotNull(locationURI);
        try (InputStream in = new BufferedInputStream(locationURI.toURL().openStream())) { // NOSONAR
            m_payload = CallWorkflowPayload.createFrom(in, getOutPortType(0));
        } catch (IOException e) {
            throw new InvalidSettingsException(e.getMessage(), e);
        }

    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        NodeParametersUtil.saveSettings(WorkflowInputSettings.class, m_settings, settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        var s = NodeParametersUtil.loadSettings(settings, WorkflowInputSettings.class);
        WorkflowIOParameterNameValidation.validateParameterName(s.m_parameterName);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings = NodeParametersUtil.loadSettings(settings, WorkflowInputSettings.class);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // TODO see Container Input (File) for possibly required cleanup

    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        //no internals
    }

    @Override
    protected void reset() {
        // this is called after {@link #setInputData(ExternalNodeData)} to prepare the node for execution.
    }

    @Override
    protected void onDispose() {
        super.onDispose();
        if (m_payload != null) {
            try {
                m_payload.close();
            } catch (IOException e) {
                getLogger().error("Error disposing payload object", e);
            }
        }
    }

    @Override
    public boolean isUseAlwaysFullyQualifiedParameterName() {
        return false;
    }

}
