/**
 * Shared user interface components for call workflow nodes.
 *
 * {@link org.knime.workflowservices.connection.util.CallWorkflowConnectionControls} to configure a connection to a KNIME Server.
 *
 * {@link org.knime.workflowservices.connection.util.ConnectionTimeoutPanel} to configure connection, load, and read
 * timeout for a server connection.
 *
 * {@link org.knime.workflowservices.connection.util.BackoffPanel} to configure retry number and wait times when
 * exceptions occur.
 *
 * {@link org.knime.workflowservices.connection.util.SelectWorkflowPanel} to enter a workflow path and browse workflows,
 * uses the {@link org.knime.workflowservices.connection.util.SelectWorkflowDialog} to pick a workflow path from a
 * repository of workflows, see {@link org.knime.workflowservices.connection.IServerConnection#listWorkflows()}.
 *
 * {@link org.knime.workflowservices.connection.util.LoadingPanel} to display an intermediate state of loading the
 * workflows in a repository.
 *
 * {@link org.knime.workflowservices.connection.util.CreateReportControls} to enable the callee workflow report generation
 * feature and select a report format (pdf, docx, etc.)
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
package org.knime.workflowservices.connection.util;