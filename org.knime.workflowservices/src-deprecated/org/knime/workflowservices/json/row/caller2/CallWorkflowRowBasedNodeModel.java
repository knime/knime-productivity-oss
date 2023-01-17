/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *

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
 *   Created on 21.07.2015 by thor
 */
package org.knime.workflowservices.json.row.caller2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.MissingCell;
import org.knime.core.data.RowKey;
import org.knime.core.data.blob.BinaryObjectCellFactory;
import org.knime.core.data.blob.BinaryObjectDataCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.json.JSONCellFactory;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.StringFormat;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.util.UniqueNameGenerator;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.knime.workflowservices.BackendExecutionResult;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.IWorkflowBackend.ReportGenerationException;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * Abstract class for nodes that call other workflows.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 *
 * @deprecated see {@link org.knime.workflowservices.json.row.caller3.CallWorkflowRowBased3NodeFactory}
 */
@Deprecated(since = "4.7.0")
public class CallWorkflowRowBasedNodeModel extends NodeModel {

    /**
     * If report creation is selected, the output table will contain a column with this base name (might have a #1
     * suffix if the input table already contains a column with the same name).
     *
     * @see #appendedColumnsSpec(DataTableSpec, RptOutputFormat, Iterable, Map)
     */
    @SuppressWarnings("javadoc")
    public static final String REPORT_COLUMN = "Report";

    /**
     * The output table will contain a status column with information about each workflow invocation. The column might
     * have a #1 suffix if the input table already contains a column with the same name.
     *
     * @see #createDataContainer(DataTableSpec, ExecutionContext, RptOutputFormat, Iterable, Map)
     */
    @SuppressWarnings("javadoc")
    public static final String STATUS_COLUMN = "Status";

    private static final NodeLogger LOGGER = NodeLogger.getLogger(CallWorkflowRowBasedNodeModel.class);

    private IServerConnection m_serverConnection;

    private final CallWorkflowRowBasedConfiguration m_configuration = new CallWorkflowRowBasedConfiguration();

    /**
     * Creates a new node model.
     */
    protected CallWorkflowRowBasedNodeModel() {
        super(new PortType[]{FileSystemPortObject.TYPE_OPTIONAL, BufferedDataTable.TYPE},
            new PortType[]{BufferedDataTable.TYPE});
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotEmpty(m_configuration.getWorkflowPath()), "No workflow path provided");

        var currentWfm = NodeContext.getContext().getWorkflowManager();
        var connectionSpec = inSpecs[0];

        m_serverConnection = ServerConnectionUtil.getConnection(connectionSpec, currentWfm);

        return new PortObjectSpec[]{null};
    }

    /** {@inheritDoc} */
    @Override
    protected BufferedDataTable[] execute(final PortObject[] inputs, final ExecutionContext exec) throws Exception {
        // If there are too many Call Local Workflow nodes pointing to the same called workflow then all threads may
        // be in use and the called workflow cannot be executed. Therefore the calling node runs invisible.
        try {
            final var outputTable = KNIMEConstants.GLOBAL_THREAD_POOL
                .runInvisible(() -> executeInternal((BufferedDataTable)inputs[1], exec));
            return new BufferedDataTable[]{outputTable};
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                throw e;
            }
        }
    }

    /**
     * Retrieves a {@link IWorkflowBackend} for the configured workflow path. Executes the workflow once for every row
     * in the input table, sending JSON cells or static JSON to the callee workflow, see
     * {@link #createWorkflowInput(Map, DataRow)}.
     *
     * @param inputTable execute callee workflow once for each row in the table
     * @param exec for creating the output container
     * @return output table, having the results of the workflow invocation appended as cells, see
     *         {@link #constructAppendedCells(BackendExecutionResult, Map, RptOutputFormat, BinaryObjectCellFactory, RowKey)}
     * @throws InvalidSettingsException
     * @throws CanceledExecutionException
     * @throws Exception
     */
    private BufferedDataTable executeInternal(final BufferedDataTable inputTable, final ExecutionContext exec)
        throws Exception {

        try (var backend = m_serverConnection.createWorkflowBackend(m_configuration)) {

            exec.setProgress("Loading workflow...");
            backend.loadWorkflow();

            exec.setProgress("Sending input data...");
            backend.updateWorkflow(m_configuration.getParameterToJsonConfigMap());

            var reportFormatOrNull = m_configuration.getReportFormat().orElse(null);

            // map workflow input parameter name to column offset in the input table that contains the JSON to be sent
            final var parameterToJsonColumnIndexMap =
                m_configuration.getParameterToJsonColumnIndexMap(inputTable.getDataTableSpec());

            // maps each callee workflow output parameter name to the offset of the cell containing the output value for
            // the parameter. Offset zero corresponds ot the first cell being appended to the input row
            Map<String, Integer> outputColIndexMap = new HashMap<>();

            // create spec and container for the additional columns
            final var appendedColumnsSpec = appendedColumnsSpec(inputTable.getDataTableSpec(), reportFormatOrNull,
                backend.getOutputValues().keySet(), outputColIndexMap);
            var appendedColumns = exec.createDataContainer(appendedColumnsSpec);

            var reportCellFactory = new BinaryObjectCellFactory(exec);

            var rowIndex = 0L;
            final var rowCount = inputTable.size();
            // execute callee workflow once for each input row
            for (DataRow row : inputTable) {
                exec.checkCanceled();
                exec.setProgress(rowIndex / (double)rowCount,
                    String.format("Row %d/%d (\"%s\")", rowIndex, rowCount, row.getKey().toString()));

                rowIndex++;

                // prepare external node data objects to be sent to callee workflow
                var workflowInput = createWorkflowInput(parameterToJsonColumnIndexMap, row);
                // if all input cells are present (none contains a missing value)
                if (workflowInput.isPresent()) {
                    // execute the workflow and retrieve results
                    var result = backend.executeWorkflow(reportFormatOrNull, workflowInput.get());
                    var appendedCells = constructAppendedCells(result, outputColIndexMap, reportFormatOrNull,
                        reportCellFactory, row.getKey());
                    appendedColumns.addRowToTable(appendedCells);
                } else {
                    appendedColumns.addRowToTable(createFailureRow(row.getKey(),
                        "Row contains missing values, workflow not called", appendedColumnsSpec.getNumColumns()));
                }
            }
            appendedColumns.close();
            return exec.createJoinedTable(inputTable, appendedColumns.getTable(), exec);
        }
    }

    /**
     * Convert JSON cells in the given input row into ExternalNodeData objects to be sent to the callee workflow.
     *
     * @param parameterToJsonColumnIndexMap maps workflow input parameter name to input table column name
     * @param r
     * @return An empty optional if any of the input cells is missing.
     */
    private Optional<Map<String, ExternalNodeData>>
        createWorkflowInput(final Map<String, Integer> parameterToJsonColumnIndexMap, final DataRow r) {

        Map<String, ExternalNodeData> input = new HashMap<>();

        for (var entry : parameterToJsonColumnIndexMap.entrySet()) {
            int colIndex = entry.getValue();
            var c = r.getCell(colIndex);
            if (c.isMissing()) {
                return Optional.empty();
            } else {
                var v = (JSONValue)c;
                String parameterId = entry.getKey();
                String parameterName = m_configuration.isDropParameterIdentifiers()
                    ? ExternalNodeData.getSimpleIDFrom(parameterId) : parameterId;
                input.put(parameterName, ExternalNodeData.builder(parameterName).jsonValue(v.getJsonValue()).build());
            }
        }
        return Optional.of(input);
    }

    /**
     * @param inSpec just to avoid column name clashes
     * @param reportFormatOrNull if non-null, add a binary report data column
     * @param outputParameterNames the callee workflow's output parameter names, one column is created for each. If drop
     *            parameter identifiers is true, the column names will be derived from the simplified parameter name
     *            (but disambiguated in case of clashes)
     * @param emptyOutputColIndexMap maps the name of the callee workflow's output parameter to a the offset of the
     *            column that contains the parameters return data (where 0 corresponds to the first appended column)
     * @return
     */
    private static DataTableSpec appendedColumnsSpec(final DataTableSpec inSpec,
        final RptOutputFormat reportFormatOrNull, final Iterable<String> outputParameterNames,
        final Map<String, Integer> emptyOutputColIndexMap) {
        var nameGen = new UniqueNameGenerator(inSpec);
        List<DataColumnSpec> columns = new ArrayList<>();
        for (String s : outputParameterNames) {
            columns.add(nameGen.newColumn(s, JSONCellFactory.TYPE));
            emptyOutputColIndexMap.put(s, emptyOutputColIndexMap.size());
        }
        if (reportFormatOrNull != null) {
            columns.add(nameGen.newColumn(REPORT_COLUMN, BinaryObjectDataCell.TYPE));
        }
        columns.add(nameGen.newColumn(STATUS_COLUMN, StringCell.TYPE));
        return new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
    }

    /**
     * @param result the results of the workflow invocation
     * @param outputColIndexMap maps a callee workflow output parameter name (as used in the
     *            {@link BackendExecutionResult} to identify the output) to the offset in the appended columns of the
     *            result cell. Offset zero corresponds to the first cell being appended to the input row.
     * @param reportFormatOrNull
     * @param reportCellFactory
     * @param rowKey key of the row in the input table for which the given results were computed
     * @return a row containing the {@link BackendExecutionResult}s. The cells in this row will be appended to the
     *         corresponding row in the input table.
     * @throws IOException if {@link BinaryObjectCellFactory#create(byte[])} fails
     */
    private static DataRow constructAppendedCells(final BackendExecutionResult result,
        final Map<String, Integer> outputColIndexMap, final RptOutputFormat reportFormatOrNull,
        final BinaryObjectCellFactory reportCellFactory, final RowKey rowKey) throws IOException {

        final var cellCount = outputColIndexMap.size() + (reportFormatOrNull != null ? 2 : 1);

        // Abort on errors
        final Optional<String> errorMessage = result.getErrorMessage();
        if (errorMessage.isPresent()) {
            return createFailureRow(rowKey, errorMessage.get(), cellCount);
        }

        var cells = new DataCell[cellCount];
        Arrays.fill(cells, DataType.getMissingCell());

        // Workflow output cells
        for (Map.Entry<String, Integer> outputColIndexEntry : outputColIndexMap.entrySet()) {
            var o = result.getJsonResults().get(outputColIndexEntry.getKey());
            if (o != null) {
                cells[outputColIndexEntry.getValue()] = JSONCellFactory.create(o);
            } else {
                cells[outputColIndexEntry.getValue()] = new MissingCell("No JSON data returned");
            }
        }

        // Report cell
        if (reportFormatOrNull != null) {
            DataCell reportCell;
            final Optional<byte[]> report = result.getReport();
            if (report.isPresent()) {
                reportCell = reportCellFactory.create(report.get());
            } else {
                // report requested but could not be generated - still output a result row
                final var reportGenerationException = result.getReportGenerationException()
                    .orElse(new ReportGenerationException("Unknown reason.", null));
                reportCell = new MissingCell(reportGenerationException.getMessage());
                LOGGER.warn("Can't generate report: " + reportGenerationException.getMessage(),
                    reportGenerationException);
            }
            cells[cells.length - 2] = reportCell;
        }

        // Status cell
        cells[cells.length - 1] =
            new StringCell("Completed in " + StringFormat.formatElapsedTime(result.getElapsedTimeMs()));

        return new DefaultRow(rowKey, cells);
    }

    /**
     * @param rowKey key of the input row to append the cells to
     * @param message value for the status column
     * @param numColumns number of missing cells to create (including status column)
     * @return missing cells for each appended output column except the {@link #STATUS_COLUMN}.
     */
    private static DataRow createFailureRow(final RowKey rowKey, final String message, final int numColumns) {
        var cells = new DataCell[numColumns];
        Arrays.fill(cells, DataType.getMissingCell());
        cells[cells.length - 1] = new StringCell(message);
        return new DefaultRow(rowKey, cells);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        new CallWorkflowRowBasedConfiguration().loadSettingsInModel(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_configuration.loadSettingsInModel(settings);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_configuration.saveSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDispose() {
        super.onDispose();
        try {
            if (m_serverConnection != null) {
                m_serverConnection.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Cannot close server connection: ", e);
        }
    }

    @Override
    protected void reset() {
        // intentionally empty
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // intentionally empty
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // intentionally empty
    }
}
