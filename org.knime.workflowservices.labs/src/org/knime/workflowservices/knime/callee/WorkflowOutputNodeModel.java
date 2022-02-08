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

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.dialog.OutputNode;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableTypeRegistry;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.workflowservices.knime.util.CallWorkflowUtil;

/**
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
final class WorkflowOutputNodeModel extends NodeModel implements OutputNode {

    public static final String DEFAULT_PARAM_NAME = "output-parameter";

    private WorkflowBoundaryConfiguration m_config = new WorkflowBoundaryConfiguration(DEFAULT_PARAM_NAME);

    /**
     * Set during execute, when the input {@link PortObject} is written to a file. The URI of the temporary file is
     * stored in this {@link Optional}.
     */
    private Optional<File> m_output = Optional.empty();

    /** If m_output is temporary and should be cleared when disposing the node: Usually this is the case but not if
     * the node was restored from an executed workflow.
     */
    private boolean m_isOutputATempFile;

    /**
     * Creates a new {@link WorkflowOutputNodeModel} with no input ports and one flow variable output port.
     *
     * @param portsConfiguration
     */
    WorkflowOutputNodeModel(final PortsConfiguration portsConfiguration) {
        super(portsConfiguration.getInputPorts(), portsConfiguration.getOutputPorts());
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        return new PortObjectSpec[] {inSpecs[0]};
    }

    /**
     * Serialize the node's input to a file that will be returned in {@link #getExternalOutput()}.
     */
    @SuppressWarnings("javadoc")
    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec) throws Exception {
        var result = inObjects[0];
        var outputFile = writePortObjectToFile(result, exec);
        m_output = Optional.of(outputFile);
        m_isOutputATempFile = true;
        return new PortObject[]{result};
    }

    private File writePortObjectToFile(final PortObject portObj, final ExecutionContext exec) throws Exception {
        if (portObj instanceof FlowVariablePortObject) {
            VariableType<?>[] allTypes = VariableTypeRegistry.getInstance().getAllTypes();
            return CallWorkflowUtil.writeFlowVariables(getAvailableFlowVariables(allTypes).values());
        } else if (portObj instanceof WorkflowPortObject) {
            return CallWorkflowUtil.writeWorkflowPortObjectAndReferencedData((WorkflowPortObject)portObj, exec);
        } else {
            return CallWorkflowUtil.writePortObject(exec, portObj);
        }
    }

    /**
     * This method serves multiple purposes. To retrieve information about the output parameters of a callee workflow
     * and to retrieve actual results.
     *
     * {@inheritDoc}
     */
    @Override
    public ExternalNodeData getExternalOutput() {
        var parameterName = m_config.getParameterName();
        return CallWorkflowUtil.createExternalNodeData(parameterName, getInPortType(0), m_output.orElse(null));
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_config.saveSettingsTo(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new WorkflowBoundaryConfiguration(DEFAULT_PARAM_NAME).loadSettingsFrom(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_config = new WorkflowBoundaryConfiguration(DEFAULT_PARAM_NAME).loadSettingsFrom(settings);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        for (File file : FileUtils.listFiles(nodeInternDir, new RegexFileFilter("output-resource\\..+"), null)) {
            m_output = Optional.of(file);
            m_isOutputATempFile = false;
        }
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        var f = m_output.orElse(null);
        if (f != null) {
            FileUtils.copyFile(f,
                new File(nodeInternDir, "output-resource." + FilenameUtils.getExtension(f.getAbsolutePath())));
        }
    }

    @Override
    protected void onDispose() {
        super.onDispose();
        reset();
    }

    /**
     * When the callee workflow is executed, all the nodes are reset. This will set the internal state of the node such
     * that during the next call to {@link #getExternalOutput()} we return a description of the workflow output, whereas
     * after running {@link #execute(PortObject[], ExecutionContext)}, {@link #getExternalOutput()} will return the
     * actual results instead of a description of the result.
     *
     * {@inheritDoc}
     */
    @SuppressWarnings("javadoc")
    @Override
    protected void reset() {
        if (m_isOutputATempFile) {
            m_output.ifPresent(f -> {
                if (!FileUtils.deleteQuietly(f)) {
                    getLogger().warnWithFormat("Unable to delete temporary file \"%s\"", f.getAbsolutePath());
                }
            });
            m_output = Optional.empty();
        }
    }

    @Override
    public boolean isUseAlwaysFullyQualifiedParameterName() {
        return false;
    }

}
