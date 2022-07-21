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
 *   Created on 6 Jul 2022 by carlwitt
 */
package org.knime.workflowservices.caller.util;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.util.StringHistory;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.IllegalFlowVariableNameException;
import org.knime.workflowservices.connection.util.SelectWorkflowPanel;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class CallWorkflowUtil {

    private CallWorkflowUtil() {
    }

    /**
     * Call workflow nodes use the {@link SelectWorkflowPanel} for selecting workflows to execute. Previous choices are
     * maintained in a {@link StringHistory}. It makes most sense to share histories across nodes that process similar
     * callee workflows.
     *
     * @author Carl Witt, KNIME AG, Zurich, Switzerland
     */
    public enum WorkflowPathHistory {
            /**
             * String history identifier for workflow paths entered in JSON-based call workflow nodes; likely those that
             * contain (Container Input/Output * nodes).
             */
            JSON_BASED_WORKFLOWS("call.workfklow.json"),

            /**
             * String history identifier for workflow paths entered in port object-based call workflow nodes; likely
             * those that use (Call Workflow Service Input/Output nodes).
             */
            PORT_OBJECT_BASED_WORKFLOWS("call.workfklow.portobject");

        final String m_historyIdentifier;

        private WorkflowPathHistory(final String historyIdentifier) {
            m_historyIdentifier = historyIdentifier;
        }

        /**
         * @return String history identifier for workflow paths
         */
        public String getIdentifier() {
            return m_historyIdentifier;
        }
    }

    /**
     * The string format used by the Call Workflow nodes to reference a local callee workflow or a workflow on a KNIME
     * Server.
     *
     * @author Carl Witt, KNIME AG, Zurich, Switzerland
     */
    public static class PlainWorkflowPathFormat {

        private static String invalidWorkflowPathMessage(final String workflowPath) {
            return String.format(
                "Invalid workflow path: \"%s\". Path must start with \"/\" or \"..\" and must not end with \"/\"",
                workflowPath);
        }

        /**
         * Check that the given path is absolute (start with '/') or relative (start with '..') and does not reference a
         * directory (ends with '/')
         *
         * @param workflowPath the path to validate
         * @return a user-facing error message in case an invalid workflow path is given, empty optional if valid
         */
        public static Optional<String> validate(final String workflowPath) {
            var valid = StringUtils.startsWithAny(workflowPath, "/", "..") && !workflowPath.endsWith("/");
            return valid ? Optional.empty() : Optional.of(invalidWorkflowPathMessage(workflowPath));
        }

    }

    /**
     * For instance the flow variable with the name "knime.workspace" is a reserved variable and can not be loaded using
     * {@link FlowVariable#load(NodeSettingsRO)} (which won't accept flow variables with reserved names).
     *
     * @param variableName the flow variable name to test for inclusion
     * @return whether the flow variable can be re-instantiated on the receiving side (the callee for a Workflow Service
     *         Input node, the caller for a Workflow Service Output node).
     */
    public static boolean verifyCredentialIdentifier(final String variableName) {
        try {
            FlowVariable.Scope.Flow.verifyName(variableName);
            return true;
        } catch (IllegalFlowVariableNameException e) { // NOSONAR
            return false;
        }
    }

}
