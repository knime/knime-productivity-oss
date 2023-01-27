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
 *   Created on 13 Jan 2023 by Dionysios Stolis
 */
package org.knime.workflowservices.connection.util;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObjectSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.contextv2.LocalLocationInfo;
import org.knime.core.node.workflow.contextv2.WorkflowContextV2.LocationType;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.connection.AbstractConnectionFactory;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Utility class that holds the static methods for establishing a connection to Workflow execution service used by the
 * Call Workflow Nodes.
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public final class ConnectionUtil {

    private ConnectionUtil() {
    }

    /**
     * Enum policy deciding what to do with failing jobs.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    public enum FailingJobRetentionPolicy {
            /**
             * Keep failing jobs.
             */
            KEEP_FAILING_JOBS,
            /**
             * Delete failing jobs.
             */
            DELETE_FAILING_JOBS;
    }

    /**
     * Enum policy deciding what to do with successful jobs.
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    public enum SuccessfulJobRetentionPolicy {
            /**
             * Keep failing jobs.
             */
            KEEP_SUCCESSFUL_JOBS,
            /**
             * Delete failing jobs.
             */
            DELETE_SUCCESSFUL_JOBS;
    }

    /** Default value for connection and read timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The default timeout to use when loading a remote workflow.
     */
    public static final Duration DEFAULT_LOAD_TIMEOUT = Duration.ofSeconds(60);

    private static ServiceTracker<AbstractConnectionFactory, AbstractConnectionFactory> connectionServiceTracker;

    static {
        Bundle coreBundle = FrameworkUtil.getBundle(ConnectionUtil.class);
        if (coreBundle != null) {
            connectionServiceTracker =
                new ServiceTracker<>(coreBundle.getBundleContext(), AbstractConnectionFactory.class, null);
            connectionServiceTracker.open();
        }
    }

    /**
     * Creates a connection for the workflow execution service, the remote execution can be handled by the Hub or Server
     * execution REST API. The factory can also provide a local execution service.
     *
     * @param configuration the Call Workflow connection configuration.
     * @return an instance of the workflow execution connection.
     */
    @SuppressWarnings("unchecked")
    public static Optional<WorkflowExecutionConnector>
        createConnection(final CallWorkflowConnectionConfiguration configuration) {
        var connectionFactory = connectionServiceTracker.getService();

        if (ObjectUtils.isNotEmpty(connectionFactory)) {
            return connectionFactory.create(configuration);
        } else {
            return Optional.empty();
        }
    }


    /**
     * Creates a workflow execution connection to access either the local or remote execution service.
     * Remote execution can either be the Hub or the KNIME Server execution REST API.
     *
     * @param configuration call workflow node connection service.
     * @return a workflow execution service implementation.
     * @throws IOException
     * @throws InvalidSettingsException when no workflow execution service is present.
     */
    public static IWorkflowBackend createWorkflowBackend(final CallWorkflowConnectionConfiguration configuration)
        throws IOException, InvalidSettingsException {
        var callWorkflowConnection = createConnection(configuration).orElseThrow(
            () -> new InvalidSettingsException("Can not create the workflow execution connection, configuration in a running job is not yet supported."));
        return callWorkflowConnection.createWorkflowBackend();
    }

    /**
     * Returns whether this connection connects to a Hub or not.
     *
     * @param fsType the connection location type (e.g FSType.HUB).
     *
     * @return <code>true</code> if it connects to a Hub, <code>false</code> otherwise
     */
    public static boolean isHubConnection(final FSType fsType) {
        if (fsType == FSType.HUB || fsType == FSType.HUB_SPACE) {
            return true;
        } else if (fsType == FSType.RELATIVE_TO_SPACE || fsType == FSType.RELATIVE_TO_WORKFLOW) {
            return isHubWorkflowContext();
        }
        return false;
    }

    private static boolean isHubWorkflowContext() {
        // WorkflowManager is null in the blue bar editor.
        if (NodeContext.getContext().getWorkflowManager() != null) {
            var context = NodeContext.getContext().getWorkflowManager().getContextV2();
            return context.getLocationType() == LocationType.HUB_SPACE;
        }
        return false;
    }

    private static boolean isRemoteWorkflowContext() {
        // WorkflowManager is null in the blue bar editor.
        if (NodeContext.getContext().getWorkflowManager() != null) {
            var context = NodeContext.getContext().getWorkflowManager().getContextV2();
            return context.getLocationType() != LocationType.LOCAL;
        } else if  (NodeContext.getContext().getWorkflowManager() == null) {
            // If workflow manager is null the workflow running in blue bar editor and its remote.
            return true;
        }
        return false;
    }

    /**
     * Used to determine whether a local or remote workflow execution connection should be created.
     *
     * @param spec the file system connector's (server connector, mount point connector, space connector) port object spec
     * @return whether a call workflow node is connected to a remote location
     */
    public static boolean isRemoteConnection(final PortObjectSpec spec) {
        // this covers the case where a connector is present, but nothing is attached to it.
        // some deprecated call workflow nodes have an optional connector port which is allowed to be left empty.
        if(spec == null) {
            return isRemoteWorkflowContext();
        }

        // in this context, only the KnimeServerConnectionInformationPortObjectSpec should occur (other implementations
        // are also remote but not related to call workflow functionality
        // e.g., GoogleCloudStorageConnectionInformationPortObjectSpec)
        if (ConnectionInformationPortObjectSpec.class.isAssignableFrom(spec.getClass())) {
            return true;
        }

        // mount points (local and remote), server connectors, space connectors
        if (spec instanceof FileSystemPortObjectSpec) {
            FileSystemPortObjectSpec fsSpec = (FileSystemPortObjectSpec)spec;
            return isRemoteConnection(fsSpec.getFSLocationSpec());
        }

        // can't handle port objects other than file system connectors
        return false;
    }


    /**
     * Used to determine whether a local or remote workflow execution connection should be created.
     *
     * This depends on the location specification and the workflow context. For instance, a location that is specified
     * relative to the current hub space acts like a LOCAL mount point connection if the location of the workflow is a
     * {@link LocalLocationInfo}, but like a space connector to the containing hub space if located on the hub.
     *
     * Connected mount points can be local (LOCAL, team space) or remote (KNIME server).
     *
     * @param fsLocation
     *
     * @return <code>true</code> if it connects to a Hub or KNIME Server, <code>false</code> otherwise
     * @throws IllegalArgumentException if the location is a mount point location and the mount point id cannot be found
     *             in the mount table
     */
    public static boolean isRemoteConnection(final FSLocationSpec fsLocation) throws IllegalArgumentException {
        var fsType = fsLocation.getFSType();
        // an arbitrary location on the executors file system
        if (fsType == FSType.LOCAL_FS) {
            return false;
        } else if (fsType == FSType.MOUNTPOINT) {
            // to find out what's behind a mount point, we consult the mount table
            String specifier = fsLocation.getFileSystemSpecifier().orElseThrow();
            final var mountId = StringUtils.removeStart(specifier, "knime-mountpoint:");
            var mountPoint = Optional.ofNullable(ExplorerMountTable.getMountPoint(mountId));
            return mountPoint.orElseThrow(() -> new IllegalArgumentException("The mount point " + mountId + " no longer exists.")).getProvider().isRemote();
        } else if (fsType == FSType.RELATIVE_TO_SPACE || fsType == FSType.RELATIVE_TO_MOUNTPOINT
            || fsType == FSType.RELATIVE_TO_WORKFLOW) {
            return isRemoteWorkflowContext();
        }
        return true;

    }

    /**
     * Check whether the given configuration can be passed to {@link WorkflowExecutionConnector#createWorkflowBackend()}
     *
     * @param configuration to check
     * @throws InvalidSettingsException if the validation fails.
     */
    public static void validateConfiguration(final CallWorkflowConnectionConfiguration configuration)
        throws InvalidSettingsException {

        if (configuration == null) {
            throw new InvalidSettingsException("Configuration is null");
        }

        // check workflow path
        var problem = configuration.validateForCreateWorkflowBackend();
        if (problem.isPresent()) {
            throw new InvalidSettingsException(problem.get());
        }

        // check report format
        boolean isHtmlFormat = configuration.getReportFormat().map(fmt -> fmt == RptOutputFormat.HTML).orElse(false);
        if (isHtmlFormat) {
            throw new InvalidSettingsException("HTML report format is not supported.");
        }
    }
}
