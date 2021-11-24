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
 *   Created on Nov 13, 2021 by wiswedel
 */
package org.knime.workflowservices.knime.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.util.FileUtil;

/**
 *
 * @author wiswedel
 */
final class PortObjectCallWorkflowPayload implements CallWorkflowPayload {

    private final PortObject m_portObject;

    private PortObjectCallWorkflowPayload(final PortObject portObject) {
        m_portObject = portObject;
    }

    @Override
    public void close() throws IOException {
        if (m_portObject instanceof Closeable) {
            ((Closeable)m_portObject).close();
        }
    }

    @Override
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo) throws Exception {
        return m_portObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PortObjectSpec getSpec() {
        return m_portObject.getSpec();
    }

    static final PortObjectCallWorkflowPayload createFrom(final InputStream input)
        throws IOException, InvalidSettingsException {
        try {
            return new PortObjectCallWorkflowPayload(PortUtil.readObjectFromStream(input, new ExecutionMonitor()));
        } catch (CanceledExecutionException e) {
            throw new InvalidSettingsException("Reading port object canceled", e);
        }
    }

    /**
     * Implementation of {@link CallWorkflowUtil#writePortObject(ExecutionContext, PortObject)} for non-table ports.
     */
    static File writePortObject(final ExecutionContext exec, final PortObject portObject)
        throws IOException, CanceledExecutionException {
        var tempFile = FileUtil.createTempFile("external-node-input-", ".portobject", false);
        PortUtil.writeObjectToFile(portObject, tempFile, exec);
        return tempFile;
    }


}
