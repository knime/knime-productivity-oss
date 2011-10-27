/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright, 2008 - 2011
  * KNIME.com, Zurich, Switzerland
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
 *   25.10.2011 (morent): created
 */

package com.knime.explorer.nodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.workflow.FlowVariable;


/**
 * This is the model implementation of ExplorerWriter.
 * Allows to write to locations mounted in KNIME explorer
 *
 * @author Dominik Morent, KNIME.com AG, Zurich, Switzerland
 */
public class ExplorerWriterNodeModel extends NodeModel {

    // the logger instance
    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(ExplorerWriterNodeModel.class);

    private static final int FOUR_MB = 40960000;

    private ExplorerWriterNodeSettings m_config;




    /**
     * Constructor for the node model.
     */
    protected ExplorerWriterNodeModel() {
        super(new PortType[]{FlowVariablePortObject.TYPE}, new PortType[0]);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects,
            final ExecutionContext exec) throws Exception {
        String filePath = peekFlowVariableString(
                m_config.getFilePathVariableName());

        File inputFile = new File(filePath);
        BufferedInputStream in = null;
        in = new BufferedInputStream(new FileInputStream(inputFile), FOUR_MB);
        URLConnection con = null;;
        BufferedOutputStream out = null;

        // Building the output URL
        String parentURL = m_config.getOutputURL();
        StringBuffer sb = new StringBuffer(parentURL);
        if (!parentURL.endsWith("/")) {
            sb.append("/");
        }
        sb.append(inputFile.getName());
        URL outputURL = new URL(sb.toString());

        LOGGER.info("Writing file \"" + filePath + "\" to URL \""
                + outputURL + ".");
        try {
            con = outputURL.openConnection();
        } catch (IOException e) {
            try {
                in.close();
            } catch (IOException e1) {
                LOGGER.error("Could not close input stream on file \" " +
                        inputFile + "\".", e);
            }
            throw new IOException("Could not open output stream.", e);
        }
        if (!m_config.isOverwriteOK() && con.getContentLength() != -1) {
            in.close();
            throw new IOException("Resource \"" + outputURL
                    + "\" exists already and \"Overwrite existing files\" "
                    + "is not checked. Writing was cancelled...");
        }
            con.setDoOutput(true);
            out = new BufferedOutputStream(
                    con.getOutputStream(), FOUR_MB);
        byte[] b = new byte[FOUR_MB];
        int len = -1;
        try {
            while ((len = in.read(b)) > 0) {
                out.write(b, 0, len);
            }
        } finally {
            in.close();
            out.close();
        }
        return new BufferedDataTable[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        Map<String, FlowVariable> flowVars = getAvailableFlowVariables();
        for (FlowVariable var : flowVars.values()) {
            if (var.getType().equals(FlowVariable.Type.STRING)) {
                return new DataTableSpec[0];
            }
        }
       throw new InvalidSettingsException("No String flow variable found.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // no op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        if (m_config != null) {
            m_config.saveSettingsTo(settings);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        ExplorerWriterNodeSettings c = new ExplorerWriterNodeSettings();
        c.loadSettingsInModel(settings);
        m_config = c;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
       ExplorerWriterNodeSettings c = new ExplorerWriterNodeSettings();
       c.loadSettingsInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // no op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // no op
    }

}

