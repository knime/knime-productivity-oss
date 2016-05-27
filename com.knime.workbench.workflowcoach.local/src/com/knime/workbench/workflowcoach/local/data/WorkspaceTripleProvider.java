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
 *   Mar 17, 2016 (hornm): created
 */
package com.knime.workbench.workflowcoach.local.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeFrequencies;
import org.knime.core.node.NodeTriple;
import org.knime.workbench.workflowcoach.data.NodeTripleProvider;
import org.osgi.framework.FrameworkUtil;

import com.knime.licenses.LicenseChecker;
import com.knime.licenses.LicenseException;
import com.knime.licenses.LicenseFeatures;
import com.knime.licenses.LicenseUtil;
import com.knime.workbench.workflowcoach.local.prefs.WorkflowCoachPreferenceInitializer;
import com.knime.workbench.workflowcoach.local.prefs.WorkflowCoachPreferencePage;

/**
 * Reads the node triples from a json file that was generated based on the local workspace.
 *
 * @author Martin Horn, KNIME.com
 */
public class WorkspaceTripleProvider implements NodeTripleProvider {

    private static final LicenseChecker LICENSE_CHECKER = new LicenseUtil(LicenseFeatures.CustomWorkflowCoach);

    /**
     * The triple json-file store within the knime workflow metadata directory.
     */
    public static final String WORKSPACE_NODE_TRIPLES_JSON_FILE =
        KNIMEConstants.getKNIMEHomeDir() + File.separator + "workspace_recommendations.json";

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<NodeTriple> getNodeTriples() throws IOException {
        return NodeFrequencies.from(new FileInputStream(WORKSPACE_NODE_TRIPLES_JSON_FILE)).getFrequencies().stream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Workspace";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Frequency of how often the nodes were used in the workflows of your workspace.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPreferencePageID() {
        return WorkflowCoachPreferencePage.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        IEclipsePreferences prefs =
            InstanceScope.INSTANCE.getNode(FrameworkUtil.getBundle(getClass()).getSymbolicName());

        return prefs.getBoolean(WorkflowCoachPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER, false)
            && Files.exists(Paths.get(WORKSPACE_NODE_TRIPLES_JSON_FILE)) && checkLicense();
    }

    /**
     * Checks the license if enabled for the {@link LicenseFeatures#CustomWorkflowCoach} feature.
     *
     * @return <code>true</code> if license exists
     */
    public static boolean checkLicense() {
        try {
            LICENSE_CHECKER.checkLicense();
            return true;
        } catch (LicenseException ex) {
            //            MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_WARNING | SWT.OK);
            //            box.setMessage("Custom Workflow Coach not available: " + ex.getMessage());
            //            box.setText("Custom Workflow Coach not available");
            //            box.open();
            return false;
        }
    }
}
