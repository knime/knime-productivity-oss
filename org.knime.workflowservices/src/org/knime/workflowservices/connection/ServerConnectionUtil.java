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
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 * @author wiswedel
 */
public final class ServerConnectionUtil {

    private static ServiceTracker<KNIMEServerAwareConnectionService, KNIMEServerAwareConnectionService> serviceTracker;

    static {
        Bundle coreBundle = FrameworkUtil.getBundle(ServerConnectionUtil.class);
        if (coreBundle != null) {
            serviceTracker = new ServiceTracker<>(coreBundle.getBundleContext(),
                    KNIMEServerAwareConnectionService.class, null);
            serviceTracker.open();
        } else {
            serviceTracker = null;
        }
    }

    public static IServerConnection getConnection(
        final PortObjectSpec spec, final WorkflowManager manager) throws InvalidSettingsException {
        var service = getService();

        // all the 'nice' method chaining on Optional not possible due to "throws" declaration
        IServerConnection serverConnection = null;
        if (service.isPresent()) {
            serverConnection =
                service.get().createKNIMEServerConnection(spec, manager.getContext()).orElse(null);
        }
        if (serverConnection == null) {
            serverConnection = new LocalExecutionServerConnection(manager);
        }
        return serverConnection;
    }

    public static IServerConnection getConnection(final String hostAndPort, final String username,
        final String password, final Duration connectTimeout, final Duration readTimeout) throws InvalidSettingsException {
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

}
