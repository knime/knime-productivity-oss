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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.knime.core.data.container.ContainerTable;
import org.knime.core.data.container.DataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.util.FileUtil;

/**
 *
 * @author wiswedel
 */
final class TableCallWorkflowPayload implements CallWorkflowPayload {

    private final ContainerTable m_containerTable;

    private TableCallWorkflowPayload(final ContainerTable containerTable) {
        m_containerTable = containerTable;
    }

    @Override
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo) throws Exception {
        return exec.createBufferedDataTable(m_containerTable, exec);
    }

    @Override
    public PortObjectSpec getSpec() {
        return m_containerTable.getDataTableSpec();
    }

    @Override
    public void close() {
        m_containerTable.close();
    }

    @SuppressWarnings("resource")
    static final TableCallWorkflowPayload createFrom(final InputStream input) throws IOException {
        return new TableCallWorkflowPayload(DataContainer.readFromStream(input));
    }

    /**
     * Implementation of {@link CallWorkflowUtil#writePortObject(ExecutionContext, PortObject)} for non-table ports.
     */
    static File writeTable(final ExecutionContext exec, final BufferedDataTable table)
        throws IOException, CanceledExecutionException {
        // BufferedDataTables are historically not port objects and have their own methods for persistence
        var tempFile = FileUtil.createTempFile("external-node-input-", ".table", false);
        DataContainer.writeToZip(table, tempFile, exec);
        return tempFile;
    }

}
