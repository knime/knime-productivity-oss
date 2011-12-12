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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.knime.core.internal.CorePlugin;
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

    private ExplorerBrowserNodeSettings m_config;

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
        publishVariables();
        return new PortObjectSpec[] {FlowVariablePortObjectSpec.INSTANCE};
    }

    private void publishVariables() throws InvalidSettingsException {
        if (m_config == null) {
            throw new InvalidSettingsException("No configuration available.");
        }

        String url = m_config.getFullOutputURL();
        if (url == null || url.isEmpty()) {
            throw new InvalidSettingsException("No configuration available.");
        }

        /* Try to resolve the URI to a local file. If this is possible the
         * absolute path is exported as flow variable as well. */
        File resolvedFile = null;
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidSettingsException("Invalid URI provided \" +"
                    + url + "\".", e);
        }
        try {
            resolvedFile = CorePlugin.resolveURItoLocalFile(uri);
            if (resolvedFile != null) {
                pushFlowVariableString("explorer_path",
                        resolvedFile.getAbsolutePath());
            } else {
                LOGGER.warn("URI \"" + uri
                        + "\" could not be resolved to a local file.");
            }
        } catch (Exception e) {
            LOGGER.warn("URI \"" + uri
                    + "\" could not be resolved to a local file.", e);
        }
        pushFlowVariableString("explorer_url", url);
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
        ExplorerBrowserNodeSettings c = new ExplorerBrowserNodeSettings();
        c.loadSettingsInModel(settings);
        m_config = c;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
       ExplorerBrowserNodeSettings c = new ExplorerBrowserNodeSettings();
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

