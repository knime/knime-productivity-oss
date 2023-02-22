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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import java.util.Optional;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * Marker interface for classes that can be used as type parameters for call workflow configuration classes.
 *
 * This includes for instance {@link LegacyPathInvocationTarget} which is used by the deprecated call workflow nodes, which specify their workflow via a plain string.
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public interface InvocationTarget {

    /**
     * Persists the invocation target (e.g., deployment ID into the node settings)
     * @param settings to save the invocation target to
     */
    void saveSettings(NodeSettingsWO settings);

    /**
     * Load the invocation target.
     * @param settings to load from
     * @throws InvalidSettingsException
     */
    void loadSettings(NodeSettingsRO settings) throws InvalidSettingsException;

    /**
     * Checks whether the invocation target is suitable for execution.
     * @throws InvalidSettingsException
     */
    void validate() throws InvalidSettingsException;

    /**
     * TODO when and why to use this and when {@link #validate()}?
     * @param settings
     * @throws InvalidSettingsException
     */
    void validate(NodeSettingsRO settings) throws InvalidSettingsException;

    /**
     * TODO probably remove
     */
    default Optional<String> validateForCreateWorkflowBackend() {
        return Optional.empty();
    }

    /**
     * @return the connectorPresent
     */
    public default boolean isConnectorPresent() {
        return getConnectorPortIndex().isPresent();
    }

    /**
     * @return the offset of the port that provides access to the callee workflow location. This port is ignored when
     *         preparing the input data for the callee.
     */
    public Optional<Integer> getConnectorPortIndex();


}
