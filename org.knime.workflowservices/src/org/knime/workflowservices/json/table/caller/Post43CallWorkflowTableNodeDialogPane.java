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

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.filehandling.core.port.FileSystemPortObjectSpec;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.BackoffPanel;
import org.knime.workflowservices.connection.util.BackoffPolicy;

/**
 * Dialog for Call Workflow Table node, added in 4.3.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
final class Post43CallWorkflowTableNodeDialogPane extends AbstractCallWorkflowTableNodeDialogPane {

    private final BackoffPanel m_backoffPanel;

    Post43CallWorkflowTableNodeDialogPane() {
        m_backoffPanel = new BackoffPanel();
        addTab("Advanced Settings", createAdvancedTab());
    }

    private JPanel createAdvancedTab() {
        final JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(m_backoffPanel);
        container.add(Box.createHorizontalGlue());
        return container;
    }

    @Override
    void saveConfiguration(final CallWorkflowTableNodeConfiguration configuration)
        throws InvalidSettingsException {
        super.saveConfiguration(configuration);
        configuration.setBackoffPolicy(m_backoffPanel.getSelectedBackoffPolicy());
    }

    @Override
    void loadConfiguration(final CallWorkflowTableNodeConfiguration configuration, final PortObjectSpec[] specs) {
        super.loadConfiguration(configuration, specs);
        m_backoffPanel.setSelectedBackoffPolicy(
            configuration.getBackoffPolicy().orElse(BackoffPolicy.DEFAULT_BACKOFF_POLICY));
        m_backoffPanel.setEnabled(specs[0] != null); // server connection present?
    }

    @Override
    IServerConnection readServerConnection(final PortObjectSpec spec, final WorkflowManager currentWFM)
            throws InvalidSettingsException {
        return ServerConnectionUtil.getConnection((FileSystemPortObjectSpec)spec, currentWFM);
    }

}
