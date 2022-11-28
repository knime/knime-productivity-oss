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
 *   Created on Nov 15, 2021 by wiswedel
 */
package org.knime.workflowservices.connection;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.contextv2.WorkflowContextV2;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 * @author wiswedel
 */
public final class ServerConnectionUtil {

    private static ServiceTracker<KNIMEServerAwareConnectionService, KNIMEServerAwareConnectionService> serviceTracker;

    private static final String RELATIVE_MOUNTPOINT = FSType.RELATIVE_TO_MOUNTPOINT.getTypeId();

    private static final String LOCAL_MOUNTPOINT = FSType.MOUNTPOINT.getTypeId() + ":LOCAL";

    static {
        Bundle coreBundle = FrameworkUtil.getBundle(ServerConnectionUtil.class);
        if (coreBundle != null) {
            serviceTracker =
                new ServiceTracker<>(coreBundle.getBundleContext(), KNIMEServerAwareConnectionService.class, null);
            serviceTracker.open();
        } else {
            serviceTracker = null;
        }
    }

    /**
     * Private constructor hiding the implicit public constructor
     */
    private ServerConnectionUtil() {
    }

    /**
     * Retrieves the correct IServerConnection implementation. Uses server and port object spec determining. 1. If
     * soleley a Server Connector is used, use the server connection. 2. If no Server Connector is connected, invoke a
     * local Call Workflow Service, using a local execution connection. 3. If a Mountpoint Connector is connected, check
     * mountpoint type: a) external mountpoint: use server connection. b) local mountpoint: use local execution
     * connection.
     *
     * @param spec the PortObjectSpec containing server or mountpoint connection
     * @param manager the workflow manager
     * @return IServerConnection that establishes connection to target
     * @throws InvalidSettingsException
     */
    public static IServerConnection getConnection(final PortObjectSpec spec, final WorkflowManager manager)
        throws InvalidSettingsException {
        var service = getService();

        // all the 'nice' method chaining on Optional not possible due to "throws" declaration
        IServerConnection serverConnection = null;
        // if the spec identifies a local mountpoint, don't create a server connection
        if (service.isPresent() && !isLocalMountpoint(spec)) {
            serverConnection = service.get().createKNIMEServerConnection(spec, manager.getContext()).orElse(null);
        }
        // non-present service OR local mountpoint connections will use the LocalExecutionServerConnection
        if (serverConnection == null) {
            serverConnection = new LocalExecutionServerConnection(manager);
        }
        return serverConnection;
    }

    /**
     * Constructs a PlainServerConnection using basic authentication with the provided credentials.
     *
     * @param hostAndPort
     * @param username
     * @param password
     * @param connectTimeout connection timeout Duration
     * @param readTimeout reading timeout Duration
     * @return IServerConnection that establishes connection to target
     * @throws InvalidSettingsException
     */
    public static IServerConnection getConnection(final String hostAndPort, final String username,
        final String password, final Duration connectTimeout, final Duration readTimeout)
        throws InvalidSettingsException {
        var service = getService().orElseThrow(() -> new InvalidSettingsException(
            String.format("No service providing remote workflow execution (implementation to \"%s\")",
                KNIMEServerAwareConnectionService.class.getName())));
        return service.createKNIMEServerConnection(hostAndPort, username, password, connectTimeout, readTimeout);
    }

    /**
     * @return
     */
    public static Optional<KNIMEServerAwareConnectionService> getService() {
        return Optional.ofNullable(serviceTracker != null ? serviceTracker.getService() : null);
    }

    /**
     * Handles the given exception and extract a pair <user-friendly-error, Throwable-cause>.
     *
     * @param ex exception to be handled
     * @return that pair.
     */
    public static Pair<String, Throwable> handle(final Exception ex) {
        Throwable cause = (ex.getCause() != null) ? ExceptionUtils.getRootCause(ex) : ex;
        String message = "Could not index workflows: "
            + getService().flatMap(s -> s.handle(cause)).orElse("Unknown reason (" + cause.getClass().getName() + ")");
        return Pair.of(message, cause);
    }

    /**
     * Determines if a spec specifies a local mountpoint connection by the following criteria: 1. has to be a
     * FileSystemPortObjectSpec 2. file system specifier matches the relative mountpoint identifier
     *
     * @param spec connector spec, can be null.
     * @return is relative mountpoint connection or false for null.
     */
    private static boolean isLocalMountpoint(final PortObjectSpec spec) {
        if (spec instanceof FileSystemPortObjectSpec) {
            var fsSpecifier = ((FileSystemPortObjectSpec)spec).getFSLocationSpec().getFileSystemSpecifier();
            return fsSpecifier.isPresent()
                && (fsSpecifier.get().equals(RELATIVE_MOUNTPOINT) || fsSpecifier.get().equals(LOCAL_MOUNTPOINT));
        }
        return false;
    }

    /**
     * Sanity check for the arguments to be passed to {@link #getConnection(PortObjectSpec, WorkflowManager)}. Does not
     * guarantee that getConnection will succeed but helps to sort out common problems early.
     *
     * @param wfm of the workflow trying to establish a connection
     * @param portObjectSpec provides details about the connection to establish. Nullable, e.g., for local connection.
     * @return error message if the workflow manager represents a temporary copy of the workflow
     */
    public static Optional<String> validate(final WorkflowManager wfm, final PortObjectSpec portObjectSpec) {
        final WorkflowContextV2 contextV2 = wfm.getContextV2();
        // Configuring a Call Workflow node requires contacting the callee to fetch its input/output parameters.
        // At the time of writing, this is not possible on the community hub thus we disable configuration here.
        if (contextV2.isTemporyWorkflowCopyMode()) {
            return Optional.of("This node cannot be configured in a temporary copy of the workflow.");
        }
        return Optional.empty();
    }
}
