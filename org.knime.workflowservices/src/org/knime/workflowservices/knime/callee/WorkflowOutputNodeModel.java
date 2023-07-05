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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.dialog.OutputNode;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectHolder;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.VariableTypeRegistry;
import org.knime.core.node.workflow.capture.WorkflowPortObject;
import org.knime.workflowservices.knime.util.CallWorkflowUtil;

/**
 * Technically, this is a {@link PortObjectHolder}, but we do not implement it to maintain backward compatibilitys. The
 * held port object is stored as a file in the internal node data.
 *
 * A copy or, if possible, hard link is created in a temporary directory in order to be able to survive a load internal
 * data/delete internal data directory/save internal data sequence of operations. This happens during a major workflow
 * version bump.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
final class WorkflowOutputNodeModel extends NodeModel implements OutputNode {

    public static final String DEFAULT_PARAM_NAME = "output-parameter";

    private static boolean hasReportedUnsupportedCreationOfHardLinks;

    private WorkflowBoundaryConfiguration m_config = new WorkflowBoundaryConfiguration(DEFAULT_PARAM_NAME);

    /**
     * Path to the temporary file containing the {@link PortObject} to return to the caller workflow.
     *
     * Set during {@link #execute(PortObject[], ExecutionContext)}, when the input {@link PortObject} is written to a
     * temporary file.
     *
     * Or set during {@link #loadInternals(File, ExecutionMonitor)} when the node is loaded in executed state.
     */
    private Path m_output;

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
        m_output = writePortObjectToTempFile(result, exec).toPath();
        return new PortObject[]{result};
    }

    private File writePortObjectToTempFile(final PortObject portObj, final ExecutionContext exec) throws Exception {
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
        var outputPath = Optional.ofNullable(m_output);
        return CallWorkflowUtil.createExternalNodeData(parameterName, getInPortType(0), outputPath.map(Path::toFile).orElse(null));
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

    /**
     * Create a hard link or copy of the port object file (internal node data) to a temporary directory.
     *
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        var files = FileUtils.listFiles(nodeInternDir, new RegexFileFilter("output-resource\\..+"), null);
        if(files.size() != 1) {
            throw new IllegalStateException("Invalid internal node data in workflow output node."
                + " There should be exactly one internal output-resource file but got: " + Arrays.toString(files.toArray()));
        }
        var portObjectFile = files.iterator().next().toPath();

        final Path tempLinkOrCopy = Files.createTempFile("output-resource", ".portobject");
        Files.delete(tempLinkOrCopy);

        m_output = hardLinkOrCopy(portObjectFile, tempLinkOrCopy);
    }

    /**
     * Store the output port object (if any) into the internal node directory.
     *
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        if (m_output != null) {
            final var file = m_output.toFile();
            FileUtils.copyFile(file,
                new File(nodeInternDir, "output-resource." + FilenameUtils.getExtension(file.getAbsolutePath())));
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
        if (m_output != null && !FileUtils.deleteQuietly(m_output.toFile())) {
            getLogger().warnWithFormat("Unable to delete temporary file \"%s\"", m_output.toAbsolutePath());
        }
        m_output = null;
    }

    @Override
    public boolean isUseAlwaysFullyQualifiedParameterName() {
        return false;
    }

    static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowOutputNodeModel.class);

    /**
     * Attempts to create a hard link from source to target to avoid unnecessary copying. If that is not supported (file
     * system doesn't support it or paths living on different file system) a copy is performed instead.
     */
    static Path hardLinkOrCopy(final Path existing, final Path link) throws IOException {
        try {
            return Files.createLink(link, existing);
        } catch (UnsupportedOperationException | FileSystemException unsupportedException) {
            if (!hasReportedUnsupportedCreationOfHardLinks) {
                hasReportedUnsupportedCreationOfHardLinks = true;
                LOGGER.warn(
                    "Creation of hard links not supported, will copy files instead (and suppress further warnings)",
                    unsupportedException);
            }
            LOGGER.debugWithFormat("Copying file %s to temp (creating of hard links not supported)", existing);
            return Files.copy(existing, link);
        }
    }

}
