/**
 * Call Workflow node for JSON-based data exchange. The favored way of calling a KNIME workflow is using the Call
 * Workflow node.
 *
 * The {@link org.knime.workflowservices.json.table.caller.CallWorkflowTableNodeFactory} does a similar thing but doesn't
 * have support for all port types and encodes data tables as JSON for sending them to the remote or local workflow,
 * which is slow and memory intensive.
 *
 * However, workflows that receive tables as JSON are still useful for integration with external tools that can not
 * write KNIME native table and port object descriptions. Thus these nodes may still be useful for testing such
 * endpoints.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
package org.knime.workflowservices.json.table.caller;