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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
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
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.exception.ResourceAccessException;
import org.knime.core.util.pathresolve.ResolverUtil;

/**
 * Allows to browse the locations mounted in KNIME explorer and exposes the
 * result as URL and absolute file path.
 *
 * @author Dominik Morent, KNIME AG, Zurich, Switzerland
 */
public class ExplorerBrowserNodeModel extends NodeModel {

    // the logger instance
    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(ExplorerBrowserNodeModel.class);

    private final ExplorerBrowserNodeSettings m_config = new ExplorerBrowserNodeSettings();

    /**
     * Constructor for the node model.
     */
    protected ExplorerBrowserNodeModel() {
        super(new PortType[0], new PortType[]{FlowVariablePortObject.TYPE});

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inObjects,
            final ExecutionContext exec) throws Exception {
        publishVariables();
        return new PortObject[] {FlowVariablePortObject.INSTANCE};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        String outputURL = CheckUtils.checkSettingNotNull(m_config.getOutputURL(), "URL must not be null");
        try {
            new URL(outputURL);
        } catch (MalformedURLException e) {
            throw new InvalidSettingsException("Invalid output URL \"" + outputURL + "\" provided.", e);
        }

        publishVariables();
        return new PortObjectSpec[] {FlowVariablePortObjectSpec.INSTANCE};
    }

    private void publishVariables() throws InvalidSettingsException {
        String urlString = m_config.getFullOutputURL();

        /* Try to resolve the URI to a local file. If this is possible the
         * absolute path is exported as flow variable as well. */
        File resolvedFile = null;
        URI uri = ExplorerBrowserNodeSettings.toURI(urlString);
        try {
            resolvedFile = ResolverUtil.resolveURItoLocalFile(uri);
            if (resolvedFile != null) {
                String explorerPath = resolvedFile.getAbsolutePath();
                // for directories append a trailing separator -- this is not really required but it used to be like
                // that in 3.2 and we want to be backward compatible
                if (resolvedFile.isDirectory()) {
                    explorerPath = StringUtils.appendIfMissing(explorerPath, File.separator, File.separator);
                }
                pushFlowVariableString("explorer_path", explorerPath);
            } else {
                LOGGER.warn("URI \"" + uri + "\" could not be resolved to a local file.");
            }
        } catch (ResourceAccessException e) {
            LOGGER.warn("URI \"" + uri + "\" could not be resolved to a local file.", e);
        }
        pushFlowVariableString("explorer_url", urlString);
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
        m_config.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_config.loadSettingsInModel(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
       ExplorerBrowserNodeSettings c = new ExplorerBrowserNodeSettings();
       c.validateSetting(settings);
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

