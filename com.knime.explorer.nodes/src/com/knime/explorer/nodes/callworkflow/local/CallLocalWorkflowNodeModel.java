/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 16, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow.local;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.workflow.NodeContext;

import com.knime.productivity.base.callworkflow.CallWorkflowConfiguration;
import com.knime.productivity.base.callworkflow.CallWorkflowNodeModel;
import com.knime.productivity.base.callworkflow.IWorkflowBackend;

/**
 * Model to node.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class CallLocalWorkflowNodeModel extends CallWorkflowNodeModel {
    private CallLocalWorkflowConfiguration m_configuration = new CallLocalWorkflowConfiguration();

    /** {@inheritDoc} */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.loadInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IWorkflowBackend newBackend(final String workflowPath) throws Exception {
        return LocalWorkflowBackend.newInstance(workflowPath, NodeContext.getContext().getWorkflowManager());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CallWorkflowConfiguration getConfiguration() {
        return m_configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_configuration.save(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new CallLocalWorkflowConfiguration().loadInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDispose() {
        LocalWorkflowBackend.cleanCalledWorkflows(NodeContext.getContext().getWorkflowManager());
    }
}
