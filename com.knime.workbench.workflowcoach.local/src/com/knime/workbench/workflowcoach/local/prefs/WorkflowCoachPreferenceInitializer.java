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
 *   Mar 15, 2016 (hornm): created
 */
package com.knime.workbench.workflowcoach.local.prefs;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.framework.FrameworkUtil;

/**
 * Initializer for the workflow coach preferences.
 *
 * @author Martin Horn, KNIME.com
 */
public class WorkflowCoachPreferenceInitializer extends AbstractPreferenceInitializer {
    /** Preference store keys. */
    public static final String P_WORKSPACE_NODE_TRIPLE_PROVIDER = "workspace_node_triple_provider";

    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences prefs =
            DefaultScope.INSTANCE.getNode(FrameworkUtil.getBundle(getClass()).getSymbolicName());
        prefs.putBoolean(P_WORKSPACE_NODE_TRIPLE_PROVIDER, false);
    }
}
