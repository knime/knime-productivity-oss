/* ------------------------------------------------------------------
  * This source code, its documentation and all appendant files
  * are protected by copyright law. All rights reserved.
  *
  * Copyright by 
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

import java.net.MalformedURLException;
import java.net.URL;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.workbench.explorer.filesystem.ExplorerFileSystem;

/**
 *
 * @author Dominik Morent, KNIME.com, Zurich, Switzerland
 *
 */
final class ExplorerWriterNodeSettings {
    private String m_filePathVarName;
    private String m_outputURL;
    private boolean m_overwriteOK;

    /**
     * @return the filePath
     */
    public String getFilePathVariableName() {
        return m_filePathVarName;
    }

    /**
     * @return the URL of the directory to save the file to
     */
    public String getOutputURL() {
        return m_outputURL;
    }

    /**
     * @param filePathVarName the filePath to set
     */
    public void setFilePathVariableName(final String filePathVarName) {
        m_filePathVarName = filePathVarName;
    }
    /**
     * @param outputURL the outputURL to set
     */
    public void setOutputURL(final String outputURL) {
        m_outputURL = outputURL;
    }


    /** Load config in model.
     * @param settings To load from.
     * @throws InvalidSettingsException If that fails for any reason.
     */
    public void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        m_filePathVarName = settings.getString("filePathVarName");
        String outputURL = settings.getString("outputURL");
        try {
            new URL(outputURL);
        } catch (MalformedURLException e) {
            throw new InvalidSettingsException("Invalid output URL \""
                    + outputURL + "\" provided.", e);
        }
        m_outputURL = outputURL;
        m_overwriteOK = settings.getBoolean("overwriteOK");
    }

    /** Load settings in dialog, init defaults if that fails.
     * @param settings To load from.
     */
    public void loadSettingsInDialog(final NodeSettingsRO settings) {
        m_filePathVarName = settings.getString("filePathVarName", "");
        m_outputURL = settings.getString("outputURL",
                ExplorerFileSystem.SCHEME + "://");
        m_overwriteOK = settings.getBoolean("overwriteOK", false);
    }

    /** @param settings to save to. */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString("filePathVarName", m_filePathVarName);
        settings.addString("outputURL", m_outputURL);
        settings.addBoolean("overwriteOK", m_overwriteOK);
    }

    /**
     * @return true, if existing files should be overwritten, false otherwise
     */
    public boolean isOverwriteOK() {
        return m_overwriteOK;
    }
    /**
     * @param overwriteOK set to true to overwrite existing files
     */
    public void setOverwriteOK(final boolean overwriteOK) {
        m_overwriteOK = overwriteOK;
    }
}
