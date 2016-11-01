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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;
import org.knime.workbench.explorer.filesystem.ExplorerFileSystem;

import com.google.common.net.UrlEscapers;

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
    String getOutputURL() {
        return m_outputURL;
    }

    /**
     * @param outputURL the outputURL to set
     */
    void setOutputURL(final String outputURL) {
        m_outputURL = outputURL;
    }

    /**
     * @return the full path including the file name
     * @throws UnsupportedEncodingException
     */
    String getFullOutputURL() throws InvalidSettingsException {
        CheckUtils.checkSettingNotNull(m_outputURL, "No configuration available");
        return getFullOutputURL(m_outputURL, m_filename);
    }

    private static String getFullOutputURL(final String rawOutputURL, final String rawFilename)
            throws InvalidSettingsException {
        String outputURL = StringUtils.removeEnd(StringUtils.defaultString(rawOutputURL), "/");
        String filename = StringUtils.removeStart(StringUtils.defaultString(rawFilename), "/");
        filename = UrlEscapers.urlPathSegmentEscaper().escape(filename);
        return String.join("/", outputURL, filename);
    }

    static URI toURI(final String urlString) throws InvalidSettingsException {
        try {
            URL url = new URL(urlString);
            return url.toURI();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new InvalidSettingsException("Cannot compose URI object from URL string \""
                    + urlString + "\": " + e.getMessage(), e);
        }
    }

    /** Load config in model.
     * @param settings To load from.
     * @throws InvalidSettingsException If that fails for any reason.
     */
    void loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_outputURL = settings.getString("outputURL");
        m_filename = settings.getString("outputFilename");
    }

    void validateSetting(final NodeSettingsRO settings) throws InvalidSettingsException {
        settings.getString("outputURL");
        settings.getString("outputFilename");
    }


    /** Load settings in dialog, init defaults if that fails.
     * @param settings To load from.
     */
    void loadSettingsInDialog(final NodeSettingsRO settings) {
        m_outputURL = settings.getString("outputURL", ExplorerFileSystem.SCHEME + "://");
        m_filename = settings.getString("outputFilename", "");
    }

    /** @param settings to save to. */
    void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString("outputURL", m_outputURL);
        settings.addString("outputFilename", m_filename);
    }

    /**
     * @return the filename
     */
    String getFilename() {
        return m_filename;
    }
    /**
     * @param filename the filename to set
     */

    void setFilename(final String filename) {
        m_filename = filename;
    }

}
