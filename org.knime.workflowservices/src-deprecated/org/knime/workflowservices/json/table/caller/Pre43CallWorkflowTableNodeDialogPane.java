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
 *   Created on Nov 13, 2020 by wiswedel
 */
package org.knime.workflowservices.json.table.caller;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Dialog for the deprecated Call Workflow Table node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @deprecated
 */
@Deprecated(since = "4.4")
final class Pre43CallWorkflowTableNodeDialogPane extends AbstractCallWorkflowTableNodeDialogPane { // NOSONAR

    @Override
    IServerConnection readServerConnection(final PortObjectSpec spec, final WorkflowManager currentWFM)
            throws InvalidSettingsException {
        return ServerConnectionUtil.getConnection(spec, currentWFM);
    }

}
