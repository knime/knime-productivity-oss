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
 *   Created on 21.07.2015 by thor
 */
package com.knime.explorer.nodes.callworkflow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
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
import org.knime.core.node.NodeModel;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.StringFormat;
import org.knime.core.util.UniqueNameGenerator;

import com.knime.explorer.nodes.callworkflow.IWorkflowBackend.WorkflowState;

/**
 * Abstract class for nodes that call other workflows.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 * @author Thorsten Meinl, KNIME.com, Zurich, Switzerland
 */
public abstract class CallWorkflowNodeModel extends NodeModel {
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
            CheckUtils.checkSetting(col != null, "Column \"%s\" does not exist in input", jsonCol);
            CheckUtils.checkSetting(col.getType().isCompatible(JSONValue.class), "Column \"%s\" does not contain JSON",
                jsonCol);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {
        BufferedDataContainer container = null;
        Map<String, Integer> outputColIndexMap = new HashMap<>();
        // the rows that fail - collected in chunks and written/flushed when we see a good case.
        Map<RowKey, String> consecutiveFailRowKeys = new LinkedHashMap<RowKey, String>();

        try (IWorkflowBackend backend = newBackend(getConfiguration().getWorkflowPath())) {
            Map<String, String> parameterToJsonColumnMap = getConfiguration().getParameterToJsonColumnMap();
            // set static input once
            // dynamic input (columns variable) is set in a loop further down below.
            backend.setInputNodes(getConfiguration().getParameterToJsonConfigMap());

            final int rowCount = inData[0].getRowCount();
            int rowIndex = 0;
            for (DataRow r : inData[0]) {
                exec.checkCanceled();
                exec.setProgress(rowIndex++ / (double)rowCount,
                    String.format("Row %d/%d (\"%s\")", rowIndex, rowCount, r.getKey().toString()));

                Map<String, ExternalNodeData> input = new LinkedHashMap<>();
                for (Map.Entry<String, String> staticEntry : parameterToJsonColumnMap.entrySet()) {
                    int colIndex = inData[0].getDataTableSpec().findColumnIndex(staticEntry.getValue());
                    DataCell c = r.getCell(colIndex);
                    if (c.isMissing()) {
                        consecutiveFailRowKeys.put(r.getKey(), "Fail. Missing input column " + staticEntry.getValue());
                        continue;
                    }
                    JSONValue v = (JSONValue)c;
                    JsonValue jsonValue = v.getJsonValue();
                    CheckUtils.checkSetting(jsonValue instanceof JsonObject,
                        "JSON in column \"%s\" is not  valid JSONObject - it's %s", staticEntry.getValue(),
                        jsonValue.getValueType());
                    JsonObject jsonObject = (JsonObject)jsonValue;
                    input.put(staticEntry.getKey(),
                        ExternalNodeData.builder(staticEntry.getKey()).jsonObject(jsonObject).build());
                }
                backend.setInputNodes(input);

                long start = System.currentTimeMillis();
                WorkflowState execute = backend.execute();
                long delay = System.currentTimeMillis() - start;

                if (!execute.equals(WorkflowState.EXECUTED)) {
                    String m = "Failure, workflow was not executed.";
                    String workflowMessage = backend.getWorkflowMessage();
                    if (StringUtils.isNotBlank(workflowMessage)) {
                        m = m + "\n" + workflowMessage;
                    }
                    consecutiveFailRowKeys.put(r.getKey(), m);
                } else {
                    Map<String, JsonObject> outputNodes = backend.getOutputValues();
                    if (container == null) {
                        container =
                            createDataContainer(inData[0].getDataTableSpec(), exec, outputNodes.keySet(), outputColIndexMap);
                    }
                    flushFailRows(container, consecutiveFailRowKeys, outputColIndexMap);
                    DataCell[] cells = new DataCell[outputColIndexMap.size() + 1];
                    Arrays.fill(cells, DataType.getMissingCell());
                    cells[cells.length - 1] = new StringCell("Completed in " + StringFormat.formatElapsedTime(delay));
                    for (Map.Entry<String, Integer> outputColIndexEntry : outputColIndexMap.entrySet()) {
                        JsonObject o = outputNodes.get(outputColIndexEntry.getKey());
                        if (o != null) {
                            cells[outputColIndexEntry.getValue()] = JSONCellFactory.create(o);
                        }
                    }
                    container.addRowToTable(new DefaultRow(r.getKey(), cells));
                }
            }
        }

        if (container == null) {
            container =
                createDataContainer(inData[0].getDataTableSpec(), exec, Collections.<String> emptyList(),
                    outputColIndexMap);
        }
        flushFailRows(container, consecutiveFailRowKeys, outputColIndexMap);
        container.close();
        return new BufferedDataTable[]{exec.createJoinedTable(inData[0], container.getTable(), exec)};
    }

    private static BufferedDataContainer createDataContainer(final DataTableSpec inSpec, final ExecutionContext exec,
        final Iterable<String> outputKeys, final Map<String, Integer> emptyOutputColIndexMap) {
        UniqueNameGenerator nameGen = new UniqueNameGenerator(inSpec);
        List<DataColumnSpec> columns = new ArrayList<>();
        for (String s : outputKeys) {
            columns.add(nameGen.newColumn(s, JSONCellFactory.TYPE));
            emptyOutputColIndexMap.put(s, emptyOutputColIndexMap.size());
        }
        columns.add(nameGen.newColumn("Status", StringCell.TYPE));
        return exec.createDataContainer(new DataTableSpec(columns.toArray(new DataColumnSpec[0])));
    }

    private static void flushFailRows(final BufferedDataContainer container,
        final Map<RowKey, String> consecutiveFailRowKeys, final Map<String, Integer> outputColIndexMap) {
        for (Map.Entry<RowKey, String> failRow : consecutiveFailRowKeys.entrySet()) {
            DataCell[] cells = new DataCell[outputColIndexMap.size() + 1];
            Arrays.fill(cells, DataType.getMissingCell());
            cells[cells.length - 1] = new StringCell(failRow.getValue());
            container.addRowToTable(new DefaultRow(failRow.getKey(), cells));
        }
        consecutiveFailRowKeys.clear();
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
