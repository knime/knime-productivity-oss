/**
 * Shared code for current and deprecated Call Workflow Service nodes.
 *
 * The Call Workflow Service node calls KNIME workflows with Workflow Service inputs (arbitrary port types).
 *
 * Similar to Workflow Executor, but the execution can be remote (instead of pulling a workflow segment into the local
 * workflow and executing it there).
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
package org.knime.workflowservices.knime.caller;