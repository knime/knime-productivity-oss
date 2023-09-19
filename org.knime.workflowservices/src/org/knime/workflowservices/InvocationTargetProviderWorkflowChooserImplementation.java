/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
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
 *   Created on 20 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.util.function.Consumer;

import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.defaultnodesettings.filechooser.workflow.SettingsModelWorkflowChooser;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @since 5.2
 * @noreference non-public API
 */
public class InvocationTargetProviderWorkflowChooserImplementation implements InvocationTargetProvider<FSLocation> {
    private final SettingsModelWorkflowChooser m_chooser;

    /**
     * @param chooser
     */
    public InvocationTargetProviderWorkflowChooserImplementation(final SettingsModelWorkflowChooser chooser) {
        m_chooser = chooser;
    }

    @Override
    public FSLocation get() {
        return m_chooser.getLocation();
    }

    @Override
    public void addChangeListener(final Consumer<FSLocation> listener) {
        m_chooser.addChangeListener(e -> listener.accept(get()));
    }

    @Override
    public boolean isLocationValid() {
        return m_chooser.isLocationValid();
    }

    @Override
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        // no need to set, is in sync already because the workflow chooser is a settings model in the configuration
    }

    @Override
    public void loadInvocationTargets(final CallWorkflowConnectionConfiguration configuration) {
        // nothing to be done, targets are listed on demand while browsing the file system
    }

    @Override
    public FSType getFileSystemType() {
        return m_chooser.getLocation().getFSType();
    }
}