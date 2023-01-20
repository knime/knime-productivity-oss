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
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.contextv2.WorkflowContextV2;
import org.knime.core.node.workflow.contextv2.WorkflowContextV2.LocationType;
import org.knime.filehandling.core.connections.FSLocationSpec;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 * @author wiswedel
 */
@Deprecated(since = "4.7.1")
public final class ServerConnectionUtil {

    private static ServiceTracker<KNIMEServerAwareConnectionService, KNIMEServerAwareConnectionService> serviceTracker;

    private static final Set<String> LOCAL_FILE_SYSTEM_SPECIFIERS = Set.of(
        // "knime-relative-mountpoint"
        // Used when
        // - no connector is present and the user has selected a callee relative to the current mount point
        // - a mountpoint connector is connected and configured to Current Mountpoint
        // - a mountpoint connector is connected and configured to Other Mountpoint: LOCAL
        FSType.RELATIVE_TO_MOUNTPOINT.getTypeId(),
        // "knime-mountpoint:LOCAL"
        // I think this is not used in current code, but I'm not sure about legacy code
        FSType.MOUNTPOINT.getTypeId() + ":LOCAL",
        // "knime-relative-space"
        // Used when
        // - no connector is present and the user has selected a callee relative to the current Hub Space
        // - a Space Connector is connected and configured to Current Space
        FSType.RELATIVE_TO_SPACE.getTypeId());

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
        var fsLocationSpec =
            spec instanceof FileSystemPortObjectSpec ? ((FileSystemPortObjectSpec)spec).getFSLocationSpec() : null;
        if (service.isPresent() && !isLocalCallee(fsLocationSpec, manager.getContextV2())) {
            serverConnection = service.get().createKNIMEServerConnection(spec, manager.getContext()).orElse(null);
        }
        // non-present service OR local mountpoint connections will use the LocalExecutionServerConnection
        if (serverConnection == null) {
            serverConnection = new LocalExecutionConnection(manager);
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
     *
     *
     * @param calleeFsLocationSpec specifies the location of the callee, e.g.,
     *            <code>(CONNECTED, knime-relative-mountpoint)</code> indicates that the callee is provided by a mount
     *            point connector configured to the LOCAL mount point
     * @param wfc the workflow context changes the target file system (callee location) if no connector is present or if
     *            it is a hub space connector (current space behaves in AP like a mount point connector configured to
     *            LOCAL)
     * @return whether to use a local workflow backend for callee execution or false for calleeFsLocationSpec = null.
     * @noreference This method is not intended to be referenced by clients.
     */
    public static boolean isLocalCallee(final FSLocationSpec calleeFsLocationSpec, final WorkflowContextV2 wfc) {
        if (calleeFsLocationSpec != null) {
            var optFsSpecifier = calleeFsLocationSpec.getFileSystemSpecifier();
            return (optFsSpecifier.isPresent() && LOCAL_FILE_SYSTEM_SPECIFIERS.contains(optFsSpecifier.get())
                && LocationType.LOCAL == wfc.getLocationType());
        }
        return false;
    }


}
