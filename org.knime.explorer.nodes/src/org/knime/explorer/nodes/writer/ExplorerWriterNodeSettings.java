/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
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

package org.knime.explorer.nodes.writer;

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
