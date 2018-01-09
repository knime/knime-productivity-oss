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
 * History
 *   25.10.2011 (morent): created
 */

package org.knime.explorer.nodes.browser;

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
 * @author Dominik Morent, KNIME AG, Zurich, Switzerland
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
     * @throws InvalidSettingsException If no output url is set
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
