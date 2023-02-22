/**
 * Configuration for execution follows a hierarchy:
 * <ol>
 * <li>The simplest case is local execution, for which only report settings are specified {@link org.knime.workflowservices.connection.configuration.LocalCallWorkflowConnectionConfiguration} </li>
 * <li>Remote execution adds timeouts, failure handling (backoff policy) and other options (discard/keep
 * successful/failed jobs) {@link org.knime.workflowservices.connection.configuration.RemoteCallWorkflowConnectionConfiguration} </li>
 * <li>Hub execution adds execution context information for ad hoc execution {@link org.knime.workflowservices.connection.configuration.HubAdHocConnectionConfiguration}</li>
 * <li> or a deployment id for deployment execution </li>
 * </ol>
 *
 * On an orthogonal plane, the {@link org.knime.workflowservices.connection.configuration.InvocationTarget} defines
 * whether the workflow to execute is specified via
 * <ul>
 * <li>a legacy format (plain string)
 * {@link org.knime.workflowservices.connection.configuration.LegacyPathInvocationTarget}</li>
 * <li>a settings model provided by the file handling framework
 * {@link org.knime.workflowservices.connection.configuration.FileSystemInvocationTarget}</li>
 * <li>or a deployment id {@link org.knime.workflowservices.connection.configuration.DeploymentInvocationTarget}</li>
 * </ul>
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
package org.knime.workflowservices.connection.configuration;