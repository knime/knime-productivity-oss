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
package org.knime.workflowservices.json.row.caller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.StringFormat;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.IllegalFlowVariableNameException;
import org.knime.core.util.UniqueNameGenerator;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.IWorkflowBackend.ReportGenerationException;
import org.knime.workflowservices.IWorkflowBackend.WorkflowState;

import jakarta.json.JsonValue;

/**
 * Base class for row-based call workflow nodes (local and remote), i.e., calling a workflow for each row in an input
 * table.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 * @author Thorsten Meinl, KNIME AG, Zurich, Switzerland
 */
@Deprecated(since = "4.7.0")
public abstract class CallWorkflowNodeModel extends NodeModel {
    /**
     *
     */
    private static final NodeLogger LOGGER = NodeLogger.getLogger(CallWorkflowNodeModel.class);

    /**
     * Creates a new node model.
     */
    protected CallWorkflowNodeModel() {
        super(1, 1);
    }

    /** {@inheritDoc} */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        CallWorkflowConfiguration config = getConfiguration();

        CheckUtils.checkSetting(config != null, "No configuration set");
        CheckUtils.checkSetting(StringUtils.isNotEmpty(config.getWorkflowPath()), "No workflow path provided");

        for (String jsonCol : getConfiguration().getParameterToJsonColumnMap().values()) {
            DataColumnSpec col = inSpecs[0].getColumnSpec(jsonCol);
            CheckUtils.checkSettingNotNull(col, "Column \"%s\" does not exist in input", jsonCol);
            CheckUtils.checkSetting(col.getType().isCompatible(JSONValue.class), "Column \"%s\" does not contain JSON",
                jsonCol);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {
        // If there are too many Call Local Workflow nodes pointing to the same called workflow then all threads may
        // be in use and the called workflow cannot be executed. Therefore the calling node runs invisible.
        try {
            return KNIMEConstants.GLOBAL_THREAD_POOL.runInvisible(new Callable<BufferedDataTable[]>() {
                @Override
                public BufferedDataTable[] call() throws Exception {
                    return executeInternal(inData, exec);
                }
            });
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else {
                throw e;
            }
        }
    }

    private BufferedDataTable[] executeInternal(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws InvalidSettingsException, CanceledExecutionException, Exception {
        Map<String, Integer> outputColIndexMap = new HashMap<>();

        try (IWorkflowBackend backend = newBackend(getConfiguration().getWorkflowPath())) {
            Map<String, String> parameterToJsonColumnMap = getConfiguration().getParameterToJsonColumnMap();
            RptOutputFormat reportFormatOrNull = getConfiguration().getReportFormatOrNull();

            // create container based on the output nodes
            backend.loadWorkflow();
            backend.updateWorkflow(getConfiguration().getParameterToJsonConfigMap());

            Collection<String> outputKeysSet = backend.getOutputValues().keySet();
            if (!getConfiguration().isUseQualifiedParameterNames()) {
                outputKeysSet = IWorkflowBackend.getFullyQualifiedToSimpleIDMap(outputKeysSet).values();
            }
            List<String> outputNodeKeys = new ArrayList<>(outputKeysSet);
            Collections.sort(outputNodeKeys);
            BufferedDataContainer container = createDataContainer(inData[0].getDataTableSpec(), exec,
                reportFormatOrNull, outputNodeKeys, outputColIndexMap);

            final long rowCount = inData[0].size();
            final int columnCount = container.getTableSpec().getNumColumns();
            long rowIndex = 0;
            BinaryObjectCellFactory reportCellFactory = new BinaryObjectCellFactory(exec);
            for (DataRow row : inData[0]) {
                exec.checkCanceled();
                exec.setProgress(rowIndex++ / (double)rowCount,
                    String.format("Row %d/%d (\"%s\")", rowIndex, rowCount, row.getKey().toString()));

                Map<String, ExternalNodeData> input = new LinkedHashMap<>();
                boolean ok = fillInputMap(inData[0].getDataTableSpec(), parameterToJsonColumnMap, row, input);
                if (ok) {
                    DataRow newRow = executeWorkflow(outputColIndexMap, backend,
                        reportFormatOrNull, reportCellFactory, row, input);
                    container.addRowToTable(newRow);
                } else {
                    container.addRowToTable(createFailureRow(row.getKey(),
                        "Row contains missing values, workflow not called", columnCount));
                }
            }
            container.close();
            return new BufferedDataTable[]{exec.createJoinedTable(inData[0], container.getTable(), exec)};
        }
    }

    private DataRow executeWorkflow(final Map<String, Integer> outputColIndexMap, final IWorkflowBackend backend,
        final RptOutputFormat reportFormatOrNull, final BinaryObjectCellFactory reportCellFactory, final DataRow inputRow,
        final Map<String, ExternalNodeData> input) throws InvalidSettingsException, Exception {

        long start = System.currentTimeMillis();
        WorkflowState state = backend.execute(input);
        long delay = System.currentTimeMillis() - start;
        // one extra column for status; and another one for the report
        final int cellCount = outputColIndexMap.size() + (reportFormatOrNull != null ? 2 : 1);

        if (!state.equals(WorkflowState.EXECUTED)) {
            String m = "Failure, workflow was not executed.";
            String workflowMessage = backend.getWorkflowMessage();
            if (StringUtils.isNotBlank(workflowMessage)) {
                m = m + "\n" + workflowMessage;
            }
            return createFailureRow(inputRow.getKey(), m, cellCount);
        } else {
            DataCell[] cells = new DataCell[cellCount];
            Arrays.fill(cells, DataType.getMissingCell());
            cells[cells.length - 1] = new StringCell("Completed in " + StringFormat.formatElapsedTime(delay));

            final Map<String, JsonValue> outputNodesFullyQualified = backend.getOutputValues();
            final Map<String, JsonValue> outputNodes;
            if (getConfiguration().isUseQualifiedParameterNames()) {
                outputNodes = outputNodesFullyQualified;
            } else {
                Map<String, String> fullyQualifiedToSimpleIDMap =
                        IWorkflowBackend.getFullyQualifiedToSimpleIDMap(outputNodesFullyQualified.keySet());
                outputNodes = fullyQualifiedToSimpleIDMap.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getValue(), entry -> outputNodesFullyQualified.get(entry.getKey())));
            }
            for (Map.Entry<String, Integer> outputColIndexEntry : outputColIndexMap.entrySet()) {
                JsonValue o = outputNodes.get(outputColIndexEntry.getKey());
                if (o != null) {
                    cells[outputColIndexEntry.getValue()] = JSONCellFactory.create(o);
                } else {
                    cells[outputColIndexEntry.getValue()] = new MissingCell("No JSON data returned");
                }
            }
            if (reportFormatOrNull != null) {
                DataCell reportCell;
                try {
                    byte[] report = backend.generateReport(reportFormatOrNull);
                    reportCell = reportCellFactory.create(report);
                } catch (IOException | ReportGenerationException e) {
                    LOGGER.warn("Can't generate report: " + e.getMessage(), e);
                    reportCell = new MissingCell(e.getMessage());
                }
                cells[cells.length - 2] = reportCell;
            }
            return new DefaultRow(inputRow.getKey(), cells);
        }
    }

    private boolean fillInputMap(final DataTableSpec inputSpec, final Map<String, String> parameterToJsonColumnMap,
        final DataRow r, final Map<String, ExternalNodeData> input) throws InvalidSettingsException {
        for (Map.Entry<String, String> staticEntry : parameterToJsonColumnMap.entrySet()) {
            int colIndex = inputSpec.findColumnIndex(staticEntry.getValue());
            DataCell c = r.getCell(colIndex);
            if (c.isMissing()) {
                return false;
            } else {
                JSONValue v = (JSONValue)c;
                input.put(staticEntry.getKey(),
                    ExternalNodeData.builder(staticEntry.getKey()).jsonValue(v.getJsonValue()).build());
            }
        }
        return true;
    }

    /**
     * @param inSpec spec of the original input table
     * @param exec for creating the data container
     * @param reportFormatOrNull report format
     * @param outputKeys the names of the columns to add to the input table
     * @param emptyOutputColIndexMap auxiliary data structure that is filled during method invocation such that it maps
     *            a new column's name (as in outputKeys) to its offset in the iterable (e.g., first new column will be
     *            mapped to 0, second to 1, etc.)
     * @return a container with the same columns as inSpec, with additional {@link JSONCellFactory} columns for each
     *         column name in outputKeys, a {@link BinaryObjectDataCell} column named "Report" if reportFormatOrNull is
     *         given, and a string column named "Status" for workflow execution information.
     */
    private static BufferedDataContainer createDataContainer(final DataTableSpec inSpec, final ExecutionContext exec,
        final RptOutputFormat reportFormatOrNull, final Iterable<String> outputKeys,
        final Map<String, Integer> emptyOutputColIndexMap) {
        UniqueNameGenerator nameGen = new UniqueNameGenerator(inSpec);
        List<DataColumnSpec> columns = new ArrayList<>();
        for (String s : outputKeys) {
            columns.add(nameGen.newColumn(s, JSONCellFactory.TYPE));
            emptyOutputColIndexMap.put(s, emptyOutputColIndexMap.size());
        }
        if (reportFormatOrNull != null) {
            columns.add(nameGen.newColumn("Report", BinaryObjectDataCell.TYPE));
        }
        columns.add(nameGen.newColumn("Status", StringCell.TYPE));
        return exec.createDataContainer(new DataTableSpec(columns.toArray(new DataColumnSpec[0])));
    }

    private static DataRow createFailureRow(final RowKey rowKey, final String message, final int numColumns) {
        DataCell[] cells = new DataCell[numColumns];
        Arrays.fill(cells, DataType.getMissingCell());
        cells[cells.length - 1] = new StringCell(message);
        return new DefaultRow(rowKey, cells);
    }

    /**
     * For instance the flow variable with the name "knime.workspace" is a reserved variable and can not be loaded using
     * {@link FlowVariable#load(NodeSettingsRO)} (which won't accept flow variables with reserved names).
     *
     * @param variableName the flow variable name to test for inclusion
     * @return whether the flow variable can be re-instantiated on the receiving side (the callee for a Workflow Service
     *         Input node, the caller for a Workflow Service Output node).
     */
    public static boolean verifyCredentialIdentifier(final String variableName) {
        try {
            FlowVariable.Scope.Flow.verifyName(variableName);
            return true;
        } catch (IllegalFlowVariableNameException e) { // NOSONAR
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec) throws IOException,
        CanceledExecutionException {
    }

    /** {@inheritDoc} */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec) throws IOException,
        CanceledExecutionException {
    }

    /**
     * This method creates a new backend for calling an external workflow.
     *
     * @param workflowPath the path to the workflow
     * @return a new backend
     * @throws Exception if an error occurs while creating the backend
     */
    protected abstract IWorkflowBackend newBackend(String workflowPath) throws Exception;

    /**
     * Returns the current configuration.
     *
     * @return a configuration, may be <code>null</code> if none has been loaded yet
     */
    protected abstract CallWorkflowConfiguration getConfiguration();
}
