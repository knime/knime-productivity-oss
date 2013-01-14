/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright, 2008 - 2013
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.knime.base.node.util.BufferedFileReader.ByteCountingStream;
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
import org.knime.core.util.FileUtil;


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
        final String filePathVariableName = m_config.getFilePathVariableName();
        String filePath = peekFlowVariableString(filePathVariableName);

        if (filePath == null || filePath.length() == 0) {
            throw new InvalidSettingsException("Path denoted by variable \""
                    + filePathVariableName + "\" is empty.");
        }

        String fileName;
        long fileSize = -1L;
        InputStream inputStream;
        try {
            URL url = new URL(filePath);
            inputStream = url.openStream();
            if ("file".equals(url.getProtocol())) {
                File f = FileUtil.getFileFromURL(url);
                if (f.exists()) {
                    fileSize = f.length();
                }
                fileName = f.getName();
            } else {
                // delete all until last slash
                String tempFileName = url.getPath().replaceFirst(".*\\/", "");
                if (tempFileName.isEmpty()) {
                    tempFileName = new SimpleDateFormat(
                            "yyyy-MM-dd_hh-mm-ss").format(
                                    new Date()).concat(".file");
                    LOGGER.warn("Could not derive file name from URL \""
                            + filePath + "\", will use \"" + tempFileName
                            + "\" instead.");
                }
                fileName = tempFileName;
            }
        } catch (MalformedURLException mue) {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new Exception("File path \"" + filePathVariableName
                        + "\" does not denote an existing file, nor "
                        + "can be parsed as URL", mue);
            }
            inputStream = new FileInputStream(file);
            fileSize = file.length();
            fileName = file.getName();
        }
        ByteCountingStream bcInStream = new ByteCountingStream(inputStream);

        URLConnection con = null;
        OutputStream out = null;

        // Building the output URL
        String parentURL = m_config.getOutputURL();
        StringBuffer sb = new StringBuffer(parentURL);
        if (!parentURL.endsWith("/")) {
            sb.append("/");
        }
        sb.append(fileName);
        URL outputURL = new URL(sb.toString());

        LOGGER.debug("Writing file \"" + filePath + "\" to URL \""
                + outputURL + ".");
        try {
            con = outputURL.openConnection();
        } catch (IOException e) {
            try {
                bcInStream.close();
            } catch (IOException e1) {
                LOGGER.error("Could not close input stream on \" " +
                        filePath + "\".", e);
            }
            throw new IOException("Could not open output stream.", e);
        }
        if (!m_config.isOverwriteOK() && con.getContentLength() != -1) {
            bcInStream.close();
            throw new IOException("Resource \"" + outputURL
                    + "\" exists already and \"Overwrite existing files\" "
                    + "is not checked. Writing was cancelled...");
        }
        con.setDoOutput(true);
        out = con.getOutputStream();
        byte[] b = new byte[1024 * 1024]; // 1MB
        int len = -1;
        try {
            while ((len = bcInStream.read(b)) > 0) {
                out.write(b, 0, len);
                exec.checkCanceled();
                long progressed = bcInStream.bytesRead();
                double sizeInMB = progressed / (double)(1024 * 1024);
                String size = NumberFormat.getInstance().format(sizeInMB);
                String message = "Writing to explorer space (" + size
                    + "MB)";
                if (fileSize > 0L) {
                    double prog = (double)progressed / fileSize;
                    exec.setProgress(prog, message);
                } else {
                    exec.setProgress(message);
                }
            }
        } finally {
            bcInStream.close();
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

