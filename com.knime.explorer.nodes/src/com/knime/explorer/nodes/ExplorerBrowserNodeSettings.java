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
final class ExplorerBrowserNodeSettings {
    private String m_outputURL;
    private String m_filename;

    /**
     * @return the URL of the directory to save the file to
     */
    public String getOutputURL() {
        return m_outputURL;
    }

    /**
     * @param outputURL the outputURL to set
     */
    public void setOutputURL(final String outputURL) {
        m_outputURL = outputURL;
    }

    /**
     * @return the full path including the file name
     */
    public String getFullOutputURL() {
        if (!m_outputURL.endsWith("/") || m_filename.startsWith("/")) {
            return m_outputURL + "/" + m_filename;
        } else {
            return m_outputURL + m_filename;
        }
    }

    /** Load config in model.
     * @param settings To load from.
     * @throws InvalidSettingsException If that fails for any reason.
     */
    public void loadSettingsInModel(final NodeSettingsRO settings)
        throws InvalidSettingsException {
        String outputURL = settings.getString("outputURL");
        try {
            new URL(outputURL);
        } catch (MalformedURLException e) {
            throw new InvalidSettingsException("Invalid output URL \""
                    + outputURL + "\" provided.", e);
        }
        m_outputURL = outputURL;
        String filename = settings.getString("outputFilename");
        try {
            new URL(outputURL + "/" + filename);
        } catch (MalformedURLException e) {
            throw new InvalidSettingsException("Invalid file name \""
                    + filename + "\" provided.", e);
        }
        m_filename = filename;
    }

    /** Load settings in dialog, init defaults if that fails.
     * @param settings To load from.
     */
    public void loadSettingsInDialog(final NodeSettingsRO settings) {
        m_outputURL = settings.getString("outputURL",
                ExplorerFileSystem.SCHEME + "://");
        m_filename = settings.getString("outputFilename", "");
    }

    /** @param settings to save to. */
    public void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString("outputURL", m_outputURL);
        settings.addString("outputFilename", m_filename);
    }

    /**
     * @return the filename
     */
    public String getFilename() {
        return m_filename;
    }
    /**
     * @param filename the filename to set
     */
    public void setFilename(final String filename) {
        m_filename = filename;
    }


}
