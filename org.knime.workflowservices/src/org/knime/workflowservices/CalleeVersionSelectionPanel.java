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
 *   Created on 23 Feb 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;

import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.TemplateUpdateUtil.LinkType;
import org.knime.core.util.hub.HubItemVersion;
import org.knime.core.util.hub.HubItemVersionPersistor;
import org.knime.core.util.hub.NamedItemVersion;

/**
 * A panel that allows to select a version for a workflow.
 *
 * <h2>Interactive life cycle</h2>
 * <ol>
 * <li>Create the panel and embed the panel into a containing dialog via {@link #getPanel()}.</li>
 * <li>Call {@link #setSelectedVersion(HubItemVersion)} to restore the version from the node settings.</li>
 * <li>Disable the panel using {@link #setLoading(boolean)}.</li>
 * <li>Async fetch the versions and call {@link #setVersions(List)}. This enables the panel again.</li>
 * <li>The user selects a version. This will notify listeners that registered via
 * {@link #addVersionChangeListener(Consumer)}.</li>
 * <li>Call {@link #getSelectedVersion()} to validate and get the selected version.</li>
 * </ol>
 *
 * <h2>Non-interactive life cycle</h2>
 * <ul>
 * <li>The constructor argument {@link FlowVariableModel} provides access to the version flow variable. See
 * {@link HubItemVersionPersistor#createFlowVariableModel(org.knime.core.node.NodeDialogPane)}</li>
 * <li>If the {@link FlowVariableModel} provides a value, {@link #onVersionFlowVariableChanged(ChangeEvent)} is called.
 * This sets the selected version.</li>
 * <li>When versions are set via {@link #setVersions(List)} the existence of the selected version is validated.</li>
 * </ol>
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @noreference non-public API
 * @since 5.2
 */
@SuppressWarnings("javadoc")
public final class CalleeVersionSelectionPanel implements Fetcher.Processor<List<NamedItemVersion>, HubItemVersion> {

    private static final NamedItemVersion LATEST_STATE =
        new NamedItemVersion(Integer.MAX_VALUE, "Latest edits", "", "", "", "");

    private static final NamedItemVersion LATEST_VERSION =
        new NamedItemVersion(Integer.MAX_VALUE, "Latest version", "", "", "", "");

    private static final Map<LinkType, NamedItemVersion> PSEUDO_VERSIONS = Map.of( //
        LinkType.LATEST_STATE, LATEST_STATE, //
        LinkType.LATEST_VERSION, LATEST_VERSION);

    /** For registering listeners on the selected hub item version. */
    private final PropertyChangeSupport m_versionProperty = new PropertyChangeSupport(this);

    /** The selected version. It is never null, but it might be invalid, see m_isVersionValid */
    private HubItemVersion m_selectedVersion = HubItemVersion.currentState();

    private boolean m_isVersionControlledByFlowVariable = false;

    /** Content of the selection drop down including pseudo versions latest edits and latest version. */
    private List<NamedItemVersion> m_versions = null;

    private boolean m_isVersionsLoading = false;

    private Optional<String> m_versionsFetchError = Optional.empty();

    private Optional<String> m_flowVariableParseError = Optional.empty();

    // controls

    private final JPanel m_panel = new JPanel();

    private final JLabel m_versionLabel = new JLabel("Version");

    private final JComboBox<NamedItemVersion> m_versionSelector = new JComboBox<>();

    private final JLabel m_statusLabel = new JLabel();

    CalleeVersionSelectionPanel(final FlowVariableModel versionModel) {
        versionModel.addChangeListener(this::onVersionFlowVariableChanged);

        m_versionSelector.setEnabled(false);

        // bind model to combo box state
        m_versionSelector.addActionListener(this::onUserInteraction);

        arrangeControls();

        // use format "Version n: title" in combo box
        m_versionSelector.setRenderer(new VersionListCellRenderer());
    }

    // ------------------- Asynchronous operations -------------------

    /**
     * Versions are fetched asynchronously. This is called on a swing worker thread.
     */
    @Override
    public synchronized void loading() {
        m_versions = null;
        m_isVersionsLoading = true;
        m_versionsFetchError = Optional.empty();
        updateState();
    }

    /**
     * The version is changed by the user. Only valid versions can be selected (i.e., versions in the currently fetched
     * list).
     *
     * This is called on the awt event dispatch thread.
     *
     * @param e only for listener compatibility
     */
    private synchronized void onUserInteraction(final ActionEvent e) {
        // ignore events fired during updates of the selector (when new versions have been fetched)
        if (!m_versionSelector.isEnabled()) {
            return;
        }

        // this can be null in case the version selector panel is hidden. It would be nicer to not even create it in this case.
        final var toStore = Optional.ofNullable((NamedItemVersion)m_versionSelector.getSelectedItem())
            .map(CalleeVersionSelectionPanel::fromNamedItemVersion).orElse(null);

        if (toStore == null) {
            // if the combo box sends null we don't care - selecting no version is not allowed but happens if there are no elements in the combo box yet
            return;
        }

        m_selectedVersion = toStore;
        updateState();
    }

    /**
     * Version is set or unset by a changed flow variable binding. The selection control is enabled or disabled
     * depending on whether the version is controlled by a flow variable.
     */
    private synchronized void onVersionFlowVariableChanged(final ChangeEvent evt) {
        try {
            final var toSet = HubItemVersionPersistor.fromFlowVariableChangeEvent(evt);
            m_flowVariableParseError = Optional.empty();
            if (toSet.isPresent()) {
                m_selectedVersion = toSet.get();
                m_isVersionControlledByFlowVariable = true;
            } else {
                m_selectedVersion = HubItemVersion.currentState();
                m_isVersionControlledByFlowVariable = false;
            }
        } catch (InvalidSettingsException e) {
            m_selectedVersion = HubItemVersion.currentState();
            m_flowVariableParseError = Optional.of(e.getMessage());
            // an exception means there is a key for the version flow variable but the associated value is invalid.
            m_isVersionControlledByFlowVariable = true;
            NodeLogger.getLogger(getClass()).warn(e);
        }
        updateState();
    }

    /**
     * The versions have been fetched. This is called on a swing worker thread.
     *
     * @param versions list of versions that can be selected (if not controlled by flow variable)
     * @param selected selection to restore, either from node settings or the version selected for a previous workflow.
     */
    @Override
    public synchronized void accept(final List<NamedItemVersion> versions) {
        // update the combo box
        m_versionSelector.removeAllItems();
        m_versionSelector.addItem(LATEST_STATE);
        if (!versions.isEmpty()) {
            m_versionSelector.addItem(LATEST_VERSION);
        }
        versions.forEach(m_versionSelector::addItem);

        m_versions = versions;
        m_isVersionsLoading = false;
        m_versionsFetchError = Optional.empty();
        updateState();
    }

    /**
     * The versions could not be fetched. This is called on a swing worker thread.
     *
     * @param message to display below the selection control.
     */
    @Override
    public synchronized void exception(final String message) {
        m_versions = null;
        m_isVersionsLoading = false;
        m_versionsFetchError = Optional.of(message);
        updateState();
    }

    /**
     * Called on the awt event dispatch thread when the location of the workflow changes.
     */
    @Override
    public synchronized void clear() {
        m_versions = null;
        m_isVersionsLoading = false;
        m_versionsFetchError = Optional.empty();
        updateState();
    }

    // ------------------- External operations -------------------

    /**
     * @return the panel containing all controls.
     */
    JPanel getPanel() {
        return m_panel;
    }

    /**
     * @param listener notified every time the selected version is changed and valid.
     */
    @Override
    public void addListener(final PropertyChangeListener listener) {
        m_versionProperty.addPropertyChangeListener(listener);
    }

    /**
     * Update the version. If it is valid, notify listeners and update the combo box selection. In case it cannot be
     * validated yet, set the state to pending.
     *
     * @param version from node settings or from user interface. Not null.
     */
    @Override
    public void set(final HubItemVersion version) {
        // check not null
        CheckUtils.checkArgumentNotNull(version);
        if (!m_isVersionControlledByFlowVariable) {
            m_selectedVersion = version;
            updateState();
        }
    }

    /**
     * @return version - either selected from the gui, defined by a flow variable, or restored by the node settings.
     *         Empty optional indicates that no valid version could be selected and the panel is not ready to proceed.
     */
    @Override
    public HubItemVersion get() {
        if (m_flowVariableParseError.isPresent()) {
            throw new IllegalStateException(m_flowVariableParseError.get());
        }
        return m_selectedVersion;
    }

    // ------------------- Internal operations -------------------

    private void updateState() {
        final var validated = findInVersions(m_selectedVersion, m_versions);

        // fire change event if the version is valid
        if (validated.isPresent()) {
            // do not fire updates when setting the selected version
            m_versionSelector.setEnabled(false);
            // combobox change events are suppressed if it is disabled
            m_versionSelector.setSelectedItem(validated.get());
            m_versionProperty.firePropertyChange("selectedVersion", null, m_selectedVersion);
        } else {
            m_versionProperty.firePropertyChange("selectedVersion", null, null);
        }

        // enable/disable selection
        final var enabled = m_versions != null && !m_isVersionsLoading && !m_isVersionControlledByFlowVariable
            && m_versionsFetchError.isEmpty() && m_flowVariableParseError.isEmpty();
        m_versionSelector.setEnabled(enabled);

        // status
        String message = null;
        // if the version is controlled by a flow variable, and invalid we show an error
        if (validated.isEmpty()) {
            message = m_isVersionControlledByFlowVariable
                ? inRed("The version %s set via flow variable does not exist.".formatted(readable(m_selectedVersion)))
                : inRed("Version %s does not exist. Switching to %s.".formatted(readable(m_selectedVersion),
                    readable(HubItemVersion.currentState())));
        }
        if (m_isVersionsLoading) {
            message = "Loading versions...";
        }
        if (m_flowVariableParseError.isPresent()) {
            message = inRed(m_flowVariableParseError.get());
        }
        if (m_versionsFetchError.isPresent()) {
            message = inRed(m_versionsFetchError.get());
        }
        m_statusLabel.setText(message != null ? message : "");
        m_statusLabel.setVisible(message != null);
    }

    /**
     * @param versionNumber to look up, not null
     * @param versions in this list
     * @return the NamedItemVersion, if present and versions is not null
     */
    private static Optional<NamedItemVersion> findInVersions(final HubItemVersion version,
        final List<NamedItemVersion> versions) {
        CheckUtils.checkArgumentNotNull(version);

        if (version.versionNumber() == null) {
            return Optional.of(PSEUDO_VERSIONS.get(version.linkType()));
        }
        if (versions == null) {
            return Optional.empty();
        }
        return versions.stream() //
            .filter(d -> d.version() == version.versionNumber()) //
            .findAny();
    }

    private static HubItemVersion fromNamedItemVersion(final NamedItemVersion niv) {
        if (niv == null) {
            return null;
        }
        if (niv == LATEST_STATE) {
            return HubItemVersion.currentState();
        }
        if (niv == LATEST_VERSION) {
            return HubItemVersion.latestVersion();
        }
        return HubItemVersion.of(niv.version());
    }

    // ------------------- Utility -------------------

    private static String inRed(final String message) {
        return """
                <html>
                <p style="color:#b20000">%s</p>
                </html>
                """.formatted(message);
    }

    private static String readable(final HubItemVersion version) {
        return switch (version.linkType()) {
            case FIXED_VERSION -> version.versionNumber().toString();
            case LATEST_VERSION -> "Latest version";
            case LATEST_STATE -> "Latest edits";
        };
    }

    /** layout the controls */
    private final void arrangeControls() {
        m_panel.setLayout(new GridBagLayout());
        final var c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;

        c.insets = new Insets(5, 0, 5, 5);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        m_panel.add(m_versionLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        m_panel.add(m_versionSelector, c);
        c.gridx = 1;
        c.gridy = 1;
        m_panel.add(m_statusLabel, c);
    }

    // ------------------- Helper classes -------------------

    /**
     * Renders the version list in the combo box. The version number is shown in the first line, the title in the second
     * line.
     */
    private static final class VersionListCellRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getListCellRendererComponent(final javax.swing.JList<?> list, final Object value,
            final int index, final boolean isSelected, final boolean cellHasFocus) {
            if (value instanceof NamedItemVersion niv) {
                var label = "Version %s: %s".formatted(niv.version(), niv.title());
                if (value == LATEST_STATE || value == LATEST_VERSION) {
                    label = niv.title();
                }
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    /**
     * @param b
     */
    public void setVisible(final boolean b) {
        getPanel().setVisible(b);
        getPanel().revalidate();
        getPanel().repaint();
    }

}
