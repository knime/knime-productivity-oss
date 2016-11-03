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
import org.knime.core.util.pathresolve.ResolverUtil;

/**
 * Allows to browse the locations mounted in KNIME explorer and exposes the
 * result as URL and absolute file path.
 *
 * @author Dominik Morent, KNIME.com AG, Zurich, Switzerland
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
        } catch (Exception e) {
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

