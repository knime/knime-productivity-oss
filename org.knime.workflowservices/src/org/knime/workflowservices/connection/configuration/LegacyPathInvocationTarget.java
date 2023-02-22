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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices.connection.configuration;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * The deprecated call workflow nodes specified the workflow to execute as a plain string.
 * This string could be
 * <ul>
 * <li> an absolute path, specifying the workflow relative to the mount point root or server repository</li>
 * <li> a relative path, specifying the workflow relative to the calling workflow</li>
 * <li> a KNIME URI, e.g., a mount point absolute, mount point relative, or workflow relative path</li>
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public class LegacyPathInvocationTarget implements InvocationTarget {

    /** Key for node settings under which the workflow path is persisted. */
    private static final String SETTINGS_KEY_WORKFLOW_PATH = "workflow";

    /** @see #getWorkflowPath() */
    String m_workflowPath;

    final boolean m_strict;

    /**
     * @param workflowPath
     */
    public LegacyPathInvocationTarget(final String workflowPath, final boolean strict) {
        m_workflowPath = workflowPath;
        m_strict = strict;
    }

    /**
     * {@inheritDoc}
     *
     * @throws InvalidSettingsException
     */
    @Override
    public void loadSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_workflowPath = m_strict ? //
            settings.getString(SETTINGS_KEY_WORKFLOW_PATH) : //
            settings.getString(SETTINGS_KEY_WORKFLOW_PATH, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        settings.addString(SETTINGS_KEY_WORKFLOW_PATH, getWorkflowPath());
    }

    @Override
    public void validate() throws InvalidSettingsException {
        validatePath(m_workflowPath);
    }

    /**
     * @return Absolute or relative path to the local or remote workflow to execute. For instance
     *         <ul>
     *         <li>/Components/Workflow</li>
     *         <li>someKnimeServerDirectory/path</li>
     *         <li>knime://knime-teamspace/callee</li>
     *         <li>knime://knime.mountpoint/callee</li>
     *         <li>knime://knime.workflow/callee</li>
     *         </ul>
     */
    public String getWorkflowPath() {
        return m_workflowPath;
    }

    /** @deprecated only used in {@link #validate(String)} */
    @Deprecated(since = "4.7.0")
    private static String invalidWorkflowPathMessage(final String workflowPath) {
        return String.format(
            "Invalid workflow path: \"%s\". Path must start with \"/\" or \"..\" and must not end with \"/\"",
            workflowPath);
    }

    /**
     * Check that the given path is absolute (start with '/') or relative (start with '..') and does not reference a
     * directory (ends with '/')
     *
     * @param workflowPath the path to validate
     * @return a user-facing error message in case an invalid workflow path is given, empty optional if valid
     * @deprecated the new call workflow nodes use the file handling framework which takes care of validation
     */
    @Deprecated(since = "4.7.0")
    private static Optional<String> validatePath(final String workflowPath) {
        var valid = StringUtils.startsWithAny(workflowPath, "/", "..") && !workflowPath.endsWith("/");
        return valid ? Optional.empty() : Optional.of(invalidWorkflowPathMessage(workflowPath));
    }

    @Override
    public Optional<Integer> getConnectorPortIndex() {
        // deprecated nodes all had a connector port (always present but optional)
        return Optional.of(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(final NodeSettingsRO settings) throws InvalidSettingsException {
        // TODO Auto-generated method stub

    }

}