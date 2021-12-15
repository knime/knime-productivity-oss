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
package org.knime.workflowservices.json.caller;

import org.knime.base.filehandling.remote.connectioninformation.port.ConnectionInformationPortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.productivity.callworkflow.table.AbstractCallWorkflowTableNodeModel;
import org.knime.productivity.callworkflow.table.Pre43ServerConnection;

import com.knime.server.nodes.util.KnimeServerConnectionInformation;
import com.knime.server.nodes.util.KnimeServerConnectionInformationPortObjectSpec;

/**
 * Model for the deprecated Call Workflow Table node.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @deprecated
 */
@Deprecated
final class Pre43CallWorkflowTableNodeModel extends AbstractCallWorkflowTableNodeModel { // NOSONAR

    Pre43CallWorkflowTableNodeModel() {
        super(ConnectionInformationPortObject.TYPE_OPTIONAL);
    }

    @Override
    Pre43ServerConnection onConfigure(final PortObjectSpec spec) {
        KnimeServerConnectionInformationPortObjectSpec connectionSpec =
            (KnimeServerConnectionInformationPortObjectSpec)spec;
        return new Pre43ServerConnection((KnimeServerConnectionInformation)connectionSpec.getConnectionInformation());
    }

}
