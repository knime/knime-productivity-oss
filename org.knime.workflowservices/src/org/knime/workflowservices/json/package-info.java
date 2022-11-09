/**
 * Call Workflow nodes using JSON-based data exchange as opposed to KNIME-native port objects.
 *
 * Workflows that receive data as JSON are useful for integration with external tools that can not write KNIME native
 * port objects descriptions.
 *
 * The input nodes (for creating callee workflows) live in the knime-json repository in the packages
 * org.knime.json.node.container.input.* and org.knime.json.node.input.
 */
package org.knime.workflowservices.json;