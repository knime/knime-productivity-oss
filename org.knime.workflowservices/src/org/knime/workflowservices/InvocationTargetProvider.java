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
 *   Created on 20 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.nio.file.NoSuchFileException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 * Abstraction over deployments and callee workflows on a hub file system.
 *
 * @param <T> location type, e.g., {@link FSLocation} or a deployment id (String).
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public interface InvocationTargetProvider<T> extends Supplier<T> {
    /**
     * To be called upon first execution to fetch the execution targets.
     *
     * @param configuration to access the list of deployments on the remote side
     * @throws RuntimeException for compatibility with completable future
     */
    void loadInvocationTargets(CallWorkflowConnectionConfiguration configuration);

    @Override
    T get();

    /**
     * @param listener to be notified when the invocation target changes.
     */
    void addChangeListener(Consumer<T> listener);

    /**
     * @return whether the result of {@link #get()} passes basic validation. Note that a space is also a valid location
     *         but does not have versions. Note that a valid location may still cause a {@link NoSuchFileException}.
     */
    boolean isLocationValid();

    /**
     * @param configuration to store the current invocation target to.
     */
    void saveToConfiguration(CallWorkflowConnectionConfiguration configuration);

    /**
     * @return the kind of file system the callee workflow is located on, or {@link FSType#HUB} for deployments.
     */
    FSType getFileSystemType();
}
