/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
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

import java.util.Map;

import javax.json.JsonValue;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;

import com.knime.enterprise.utility.oda.ReportingConstants.RptOutputFormat;

/**
 * Interface to access a workflow. Can be either a local workflow or a remote flow (via REST calls then).
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
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
}
