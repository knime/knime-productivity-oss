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
 *   Created on Nov 14, 2020 by wiswedel
 */
package org.knime.workflowservices.json.table.caller;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Model for Call Workflow Table node, added in 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class Post43CallWorkflowTableNodeModel extends AbstractCallWorkflowTableNodeModel {

    Post43CallWorkflowTableNodeModel() {
        super(FileSystemPortObject.TYPE_OPTIONAL);
    }

    @Override
    IServerConnection onConfigure(final PortObjectSpec connectionSpec, final WorkflowManager currentWfm)
        throws InvalidSettingsException {
        return ServerConnectionUtil.getConnection((FileSystemPortObjectSpec)connectionSpec, currentWfm);
    }

}
