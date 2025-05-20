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
 *   Created on 20 May 2025 by Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
package org.knime.workflowservices.knime.callee;

/**
 * Settings class for Workflow Service Input node.
 *
 * @author Manuel Hotz, KNIME GmbH, Konstanz, Germany
 */
final class WorkflowInputSettings extends WorkflowBoundaryConfiguration {

    WorkflowInputSettings() {
        super(WorkflowInputNodeModel.DEFAULT_PARAM_NAME);
    }
}
