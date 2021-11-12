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
 *   Created on Feb 17, 2015 by wiswedel
 */
package org.knime.productivity.base.callworkflow;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.json.JsonValue;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortTypeRegistry;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;

/**
 * Interface to access a workflow. Can be either a local workflow or a remote flow (via REST calls then).
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public interface IWorkflowBackend extends AutoCloseable {
    /** Wraps the workflow state - either translates to node container state (local) or the REST version. */
    public enum WorkflowState {
        IDLE, RUNNING, EXECUTED,
    }

    /**
     * Returns a map of the input nodes in the external workflow. The map keys are the unique input IDs.
     *
     * @return a map of input nodes
     */
    Map<String, ExternalNodeData> getInputNodes();

    /**
     * Sets the input nodes for the workflow. The map should have the same structure as the one returned by
     * {@link #getInputNodes()} but with potententially updated values.
     *
     * @param input a map with the updated input data
     * @throws InvalidSettingsException
     */
    void setInputNodes(Map<String, ExternalNodeData> input) throws InvalidSettingsException;

    /**
     * Returns a map with the output values of the called workflow. That map keys are the unique output IDs.
     *
     * @return a map between IDs and values
     */
    Map<String, JsonValue> getOutputValues();

    Map<String, ResourceContentType> getInputResourceDescription() throws InvalidSettingsException;

    Map<String, ResourceContentType> getOutputResourceDescription() throws InvalidSettingsException;

    InputStream openOutputResource(final String name) throws IOException;

    /**
     * Executes the workflow and returns the state after execution. The map doesn't need to contain all input values
     * but only the ones that have changed
     *
     * @param input a map with the updated input data
     * @return the current workflow state
     * @throws Exception if an error occurs during execution
     */
    WorkflowState execute(final Map<String, ExternalNodeData> input) throws Exception;

    /**
     * Returns the messages that occurred during execution.
     *
     * @return the messages or an empty string if there are no messages
     */
    String getWorkflowMessage();

    /**
     * @param format
     * @return
     */
    byte[] generateReport(final RptOutputFormat format) throws ReportGenerationException;

    /** Thrown by {@link IWorkflowBackend#generateReport(RptOutputFormat)} in case the report could not be generated. */
    public final class ReportGenerationException extends Exception {

        /**
         * @param message
         * @param cause
         */
        public ReportGenerationException(final String message, final Throwable cause) {
            super(message, cause);
        }

    }

    /** For all parameters in the collection get the simple ID if applicable, other the full id. For instance,
     * if the argument is [string-input-1, string-input-2, int-input-3], the result will be:
     * <ul>
     * <li>string-input-1 -> string-input-1
     * <li>string-input-2 -> string-input-2
     * <li>int-input-3 -> int-input
     * </ul>
     * @param fullyQualfiedIDs non-null collection with all parameters, fully qualified
     * @return the map as describe above
     */
    public static Map<String, String> getFullyQualifiedToSimpleIDMap(final Collection<String> fullyQualfiedIDs) {
        Map<String, Long> collisionMap = fullyQualfiedIDs.stream()
                .collect(Collectors.groupingBy(ExternalNodeData::getSimpleIDFrom, Collectors.counting()));
        collisionMap.values().removeIf(l -> l.longValue() > 1L);

        return fullyQualfiedIDs.stream().collect(Collectors.toMap(Function.identity(), name -> {
            String simpleID = ExternalNodeData.getSimpleIDFrom(name);
            return collisionMap.containsKey(simpleID) ? simpleID : name;
        }));
    }

    // TODO javadoc
    public class ResourceContentType {

        /** The content type of in/output node's {@link ExternalNodeData} starts with this and is followed by the fully
         * qualified class name of the port type, e.g. <pre>knime-port/org.knime.core.node.port.pmml.PMMLPortObject</pre>.
         */
        public static final String CONTENT_TYPE_DEF_PREFIX = "knime-port/";

        private final String m_contentType;

        /**
         *
         */
        private ResourceContentType(final String contentType) {
            m_contentType = CheckUtils.checkNotNull(contentType);
        }

        public String asString() {
            return m_contentType;
        }

        @Override
        public String toString() {
            return asString();
        }

        public boolean isKNIMEPortType() {
            return m_contentType.startsWith(CONTENT_TYPE_DEF_PREFIX);
        }

        public PortType toPortType() throws InvalidSettingsException {
            CheckUtils.checkSetting(isKNIMEPortType(), "content type does not represent a KNIME port type: %s",
                m_contentType);
            var className = m_contentType.substring(CONTENT_TYPE_DEF_PREFIX.length());
            return PortTypeRegistry.getInstance().availablePortTypes().stream() //
                .filter(p -> p.getPortObjectClass().getName().equals(className)) //
                .findFirst().orElseThrow(() -> new InvalidSettingsException(
                    String.format("Can not instantiate port object for class %s - class not found.", className)));
        }

        public static ResourceContentType of(final PortType portType) {
            return new ResourceContentType(CONTENT_TYPE_DEF_PREFIX + portType.getPortObjectClass().getName());
        }

        public static ResourceContentType of(final String contentType) {
            return new ResourceContentType(contentType);
        }

    }

}
