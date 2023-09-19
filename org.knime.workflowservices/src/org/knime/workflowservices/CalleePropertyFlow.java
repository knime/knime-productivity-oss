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
 *   Created on 19 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

/**
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public interface CalleePropertyFlow {

    /**
     * Used to disable during loading when the dialog is in inconsistent state.
     *
     * @param enabled whether the flow should react to change events or ignore them.
     */
    void enable(boolean enabled);

    /**
     * Asynchronously execute the
     * {@link InvocationTargetProvider#loadInvocationTargets(org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration)}
     * and call {@link #invocationTargetUpdated()} afterwards.
     */
    void loadInvocationTargets();

    /**
     * Fetch properties of the invocation target, e.g., workflow parameters.
     */
    void invocationTargetUpdated();

    /**
     * Cancel any background processes.
     */
    void close();

    /**
     * @return
     */
    InvocationTargetProvider<?> getInvocationTarget();

}