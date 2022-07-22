package org.knime.workflowservices.connection.util;

import java.awt.Dimension;
import java.awt.Frame;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.util.StringHistory;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;

/**
 * {@link JPanel} to edit a workflow path manually or select a workflow from a list of workflows.
 *
 * This is not a dialog component because it doesn't read/write from/to NodeSettings but from/to
 * {@link CallWorkflowConnectionConfiguration}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class SelectWorkflowPanel {

    private final class Controls {
        /** contains all controls */
        private final JPanel m_panel = new JPanel();

        private final JLabel m_workflowLabel = new JLabel("Workflow Path: ");

        /** Calls on every change. */
        private final JTextField m_selectWorkflowPath = new JTextField();

        /**
         * Shows the identifiers (paths) of locally or remotely available workflows. An identifier can be selected in
         * the dialog and will be entered in the text box that allows manual editing of the path.
         */
        private final JButton m_selectWorkflowButton = new JButton("Browse workflows");

        private final SelectWorkflowDialog m_selectWorkflowDialog;

        /**
         * Shows previously used identifiers (paths) of locally or remotely available workflows.
         */
        private final JButton m_selectRecentWorkflowButton = new JButton("Recent choices");

        private final SelectWorkflowDialog m_selectRecentWorkflowDialog;

        private Controls(final JPanel jPanel) {

            var parentFrame = getParentFrame(jPanel);
            m_selectWorkflowDialog = new SelectWorkflowDialog(parentFrame, SelectWorkflowPanel.this::setWorkflowPath,
                SelectWorkflowPanel.this::fetchRemoteWorkflows);

            m_selectRecentWorkflowDialog = new SelectWorkflowDialog(parentFrame, SelectWorkflowPanel.this::setWorkflowPath,
                SelectWorkflowPanel.this::listRecentWorkflows);

            m_selectWorkflowPath.setPreferredSize(new Dimension(500, 20));
            m_selectWorkflowPath.setMaximumSize(new Dimension(1000, 20));
            m_selectWorkflowPath.setEditable(true);

            m_panel.setBorder(BorderFactory.createTitledBorder("Workflow to execute"));

            // glue eats the space, text box should grow more
            m_panel.setLayout(new BoxLayout(m_panel, BoxLayout.X_AXIS));
            m_panel.add(Box.createRigidArea(new Dimension(5, 30)));
            m_panel.add(m_workflowLabel);
            m_panel.add(m_selectWorkflowPath);
            m_panel.add(m_selectWorkflowButton);
            m_panel.add(m_selectRecentWorkflowButton);
            m_panel.add(Box.createHorizontalGlue());
        }

        private void setOnChange(final Consumer<String> callback) {

            m_selectWorkflowPath.getDocument().addDocumentListener(new DocumentListener() { //NOSONAR
                @Override
                public void removeUpdate(final DocumentEvent e) {
                    // not nice: when setting the text of the input,
                    // it will fire a remove update (setting to "" first) and then an insert update
                    // causing an error to be shown (Empty string is an invalid path) for a tiny moment
                    anyUpdate(e);
                }

                @Override
                public void insertUpdate(final DocumentEvent e) {
                    anyUpdate(e);
                }

                @Override
                public void changedUpdate(final DocumentEvent e) {
                    anyUpdate(e);
                }

                private void anyUpdate(final DocumentEvent e) {
                    var doc = e.getDocument();
                    try {
                        callback.accept(doc.getText(0, doc.getLength()));
                    } catch (BadLocationException e1) { // NOSONAR
                        // doesn't happen
                    }
                }
            });

            // combobox workflow path editor has some problems
            // addActionListener doesn't react to dropdown select but to enter keypress
            // getEditorComponent().addPropertyChangeListener reacts only to enabled, graphics conf, ancestor, etc.
            // getEditorComponent().addKeyListener doesn't react to drop down select and doesn't work with copy/paste
        }
    }

    private Optional<Callable<List<String>>> m_listWorkflows = Optional.empty();

    private final Controls m_controls;

    /** Stores recently used workflow paths for later quick selection. */
    private final StringHistory m_history;

    private Frame m_parentFrame;

    /**
     * Create a panel without a callback when the user (de-)selects "Create Report"
     *
     * @param jPanel
     *
     * @param workflowPathHistoryId which string history to use to populate the workflowpath combobox with previous
     *            choices.
     */
    public SelectWorkflowPanel(final JPanel jPanel, final String workflowPathHistoryId) {
        m_controls = new Controls(jPanel);
        m_controls.m_selectWorkflowButton.addActionListener(e -> m_controls.m_selectWorkflowDialog.open());
        m_controls.m_selectRecentWorkflowButton.addActionListener(e -> m_controls.m_selectRecentWorkflowDialog.open());

        m_history = StringHistory.getInstance(workflowPathHistoryId, 100);

        // disable all user elements until a local or remote connection is given to browse workflows
        setServerConnection(null);
    }

    // async

    /**
     * Get a list of workflow paths from the connected KNIME Server. This may take a while and is done asynchronously.
     * The worker will call {@link SelectWorkflowDialog#setWorkflowPaths(List)} on {@link #m_selectWorkflowDialog}.
     *
     * @throws Exception
     */
    private List<String> fetchRemoteWorkflows() throws Exception {
        if (m_listWorkflows.isEmpty()) {
            return List.of();
        }
        List<String> listedWorkflows;
        try {
            listedWorkflows = m_listWorkflows.get().call();
        } catch (ListWorkflowFailedException e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            throw rootCause instanceof Exception ? (Exception)rootCause : e;
        }
        Collections.sort(listedWorkflows, String.CASE_INSENSITIVE_ORDER);
        return listedWorkflows;
    }

    /**
     * To populate the recent workflow paths dialog.
     */
    private List<String> listRecentWorkflows() {
        return Arrays.stream(m_history.getHistory()).collect(Collectors.toList());
    }

    // load & save

    /**
     * @param configuration
     */
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        configuration.setWorkflowPath(getWorkflowPath());
        m_history.add(getWorkflowPath());
    }

    /**
     * @param configuration
     */
    public void loadConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        setWorkflowPath(configuration.getWorkflowPath());
    }

    // getter & setter

    /**
     * @param panel the node dialog's getPanel() to display the browse/select dialogs on the same monitor as the dialog
     */
    public void setParentFrame(final JPanel panel) {
        m_parentFrame = getParentFrame(panel);
    }

    /**
     * @return the panel containing all controls
     */
    public JComponent getPanel() {
        return m_controls.m_panel;
    }

    /**
     * @param serverConnection the serverConnection to set
     */
    public void setServerConnection(final IServerConnection serverConnection) {
        enableAllUIElements(serverConnection != null);
    }

    /**
     * @param path the string to display in the text box to manually edit the workflow path
     */
    public void setWorkflowPath(final String path) {
        m_controls.m_selectWorkflowPath.setText(path);
    }

    /**
     * @return an identifier for a workflow (local or remote)
     */
    public String getWorkflowPath() {
        return m_controls.m_selectWorkflowPath.getText();
    }

    // utility

    /**
     *
     * @param nodeDialogPanel
     * @return
     */
    private static Frame getParentFrame(final JPanel panel) {
        Frame frame = null;
        var container = panel.getParent();
        while (container != null) {
            if (container instanceof Frame) {
                frame = (Frame)container;
                break;
            }
            container = container.getParent();
        }
        return frame;
    }

    /**
     * @param enable
     */
    public void enableAllUIElements(final boolean enable) {
        m_controls.m_panel.setEnabled(enable);
        m_controls.m_workflowLabel.setEnabled(enable);
        m_controls.m_selectWorkflowButton.setEnabled(enable);
        m_controls.m_selectRecentWorkflowButton.setEnabled(enable);
        m_controls.m_selectWorkflowPath.setEnabled(enable);

    }

    /**
     * @param workflowPathChangedCallback the workflowPathChangedCallback to set
     */
    public void setWorkflowPathChangedCallback(final Consumer<String> workflowPathChangedCallback) {
        m_controls.setOnChange(workflowPathChangedCallback);
    }

    /**
     * @param listWorkflows the logic to call to initialize and refresh the browse workflows dialog.
     */
    public void setListWorkflows(final Callable<List<String>> listWorkflows) {
        m_listWorkflows = Optional.ofNullable(listWorkflows);
    }

}