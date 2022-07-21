package org.knime.workflowservices.connection.util;

import java.awt.Dimension;
import java.awt.Frame;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.IServerConnection;
import org.knime.workflowservices.connection.IServerConnection.ListWorkflowFailedException;
import org.knime.workflowservices.connection.ServerConnectionUtil;

/**
 * {@link JPanel} to edit a workflow path manually or select a workflow from a list of workflows.
 *
 * This is not a dialog component because it doesn't read/write from/to NodeSettings but from/to
 * {@link CallWorkflowConnectionConfiguration}.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class SelectWorkflowPanel {

    // TODO make dialog appear on main screen
    /**
     * Extension of a swing worker that fetches all workflows available from a remote server;
     *
     * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
     */
    final class ListWorkflowsWorker extends SwingWorkerWithContext<List<String>, Void> {

        @Override
        protected List<String> doInBackgroundWithContext() throws Exception {
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
         * {@inheritDoc}
         */
        @Override
        protected void doneWithContext() {
            try {
                List<String> listedWorkflows = get();
                m_controls.m_selectWorkflowDialog.setWorkflowPaths(listedWorkflows);
                m_listWorkflowsWorker.set(null);
            } catch (InterruptedException | CancellationException ex) {
                m_controls.m_selectWorkflowDialog.clearWorkflowList();
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                m_controls.m_selectWorkflowDialog.clearWorkflowList();
                var pair = ServerConnectionUtil.handle(ex);
                m_controls.m_selectWorkflowDialog.setErrorText(pair.getLeft());
                // TODO logging
                //                LOGGER.error(pair.getLeft(), pair.getRight());
            }
        }

    }

    private final class Controls {
        /** contains all controls */
        private final JPanel m_panel = new JPanel();

        /**
         * Shows the identifiers (paths) of locally or remotely available workflows. An identifier can be selected in
         * the dialog and will be entered in the text box that allows manual editing of the path.
         */
        private final SelectWorkflowDialog m_selectWorkflowDialog;

        private final JLabel m_workflowLabel = new JLabel("Workflow Path: ");

        /** Calls on every change. */
        private final JTextField m_selectWorkflowPath = new JTextField();

        private final JButton m_selectWorkflowButton = new JButton("Browse workflows");

        /**
         *
         * @param dialogParentFrame parent frame for the browse workflows dialog
         */
        Controls(final Frame dialogParentFrame) {
            m_selectWorkflowDialog = new SelectWorkflowDialog(dialogParentFrame,
                SelectWorkflowPanel.this::setWorkflowPath, SelectWorkflowPanel.this::fetchRemoteWorkflows);

            m_selectWorkflowPath.setPreferredSize(new Dimension(500, 20));
            m_selectWorkflowPath.setMaximumSize(new Dimension(1000, 20));
            m_selectWorkflowPath.setEditable(true);

            m_panel.setBorder(BorderFactory.createTitledBorder("Workflow to execute"));

            // doesn't respect max height, looks weird
            //            m_panel.setLayout(new BorderLayout(5, 5));
            //            m_panel.add(m_workflowLabel, BorderLayout.LINE_START);
            //            m_panel.add(m_selectWorkflowPath, BorderLayout.CENTER);
            //            m_panel.add(m_selectWorkflowButton, BorderLayout.LINE_END);

            // glue eats the space, text box should grow more
            m_panel.setLayout(new BoxLayout(m_panel, BoxLayout.X_AXIS));
            m_panel.add(Box.createRigidArea(new Dimension(5, 30)));
            m_panel.add(m_workflowLabel);
            m_panel.add(m_selectWorkflowPath);
            m_panel.add(m_selectWorkflowButton);
            m_panel.add(Box.createHorizontalGlue());

            //
            //            m_panel.setLayout(new FlowLayout(FlowLayout.LEADING, 5, 5));
            //            m_panel.add(m_workflowLabel);
            //            m_panel.add(m_selectWorkflowPath);
            //            m_panel.add(m_selectWorkflowButton);

            //            var gbc = new GridBagConstraints();
            //            gbc.anchor = GridBagConstraints.WEST;
            //
            //            gbc.fill = GridBagConstraints.NONE;
            //            m_panel.add(m_workflowLabel, gbc);
            //
            //            gbc.gridx++;
            //            gbc.fill = GridBagConstraints.HORIZONTAL;
            //            m_panel.add(m_selectWorkflowPath, gbc);
            //
            //            gbc.gridx++;
            //            gbc.fill = GridBagConstraints.NONE;
            //            m_panel.add(m_selectWorkflowButton, gbc);
        }

        void setOnChange(final Consumer<String> callback) {

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

            // doesn't react to dropdown select but to enter keypress
            //            m_selectWorkflowPath.getComboBox().getEditor().addActionListener(e -> {
            //                    System.out.println("action listener fired");
            //                    callback.accept(m_selectWorkflowPath.getSelectedString());
            //                });

            // reacts only to enabled, graphics conf, ancestor, etc.
            //            m_selectWorkflowPath.getComboBox().getEditor().getEditorComponent().addPropertyChangeListener(new PropertyChangeListener() {
            //
            //                @Override
            //                public void propertyChange(final PropertyChangeEvent evt) {
            //                    System.out.println("Property changed");
            //                    System.out.println(evt);
            //
            //                }
            //            });

            // doesn't react to drop down select
            // doesn't work with copy/paste
            //            m_selectWorkflowPath.getComboBox().getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            //                @Override
            //                public void keyTyped(final KeyEvent e) {
            //                    System.out.println("key typed listener fired");
            //                    callback.accept(m_selectWorkflowPath.getSelectedString());
            //                }
            //
            //            });
        }
    }

    private Optional<IServerConnection> m_serverConnection = Optional.empty();

    /** Asynchronously fetches available workflows from a KNIME Server. */
    private final AtomicReference<ListWorkflowsWorker> m_listWorkflowsWorker = new AtomicReference<>(null);

    private Optional<Callable<List<String>>> m_listWorkflows = Optional.empty();

    private final Controls m_controls;

    /** Stores recently used workflow paths for later quick selection. */
    private final StringHistory m_history;

    /**
     * Create a panel without a callback when the user (de-)selects "Create Report"
     *
     * @param nodeDialogPanel contains this panel
     * @param workflowPathHistoryId which string history to use to populate the workflowpath combobox with previous
     *            choices.
     */
    public SelectWorkflowPanel(final JPanel nodeDialogPanel, final String workflowPathHistoryId) {

        m_history = StringHistory.getInstance(workflowPathHistoryId);

        m_controls = new Controls(getParentFrame(nodeDialogPanel));

        m_controls.m_selectWorkflowButton.addActionListener(l -> m_controls.m_selectWorkflowDialog.open());

        // disable all user elements until a local or remote connection is given to browse workflows
        setServerConnection(null);
    }

    // async

    /**
     * Get a list of workflow paths from the connected KNIME Server. This may take a while and is done asynchronously.
     * The worker will call {@link SelectWorkflowDialog#setWorkflowPaths(List)} on {@link #m_selectWorkflowDialog}.
     */
    private void fetchRemoteWorkflows() {
        if (m_listWorkflowsWorker.get() == null) {
            var worker = new ListWorkflowsWorker();
            m_listWorkflowsWorker.set(worker);
            worker.execute();
        }
    }

    /**
     * Try to stop any ongoing asynchronous fetch workflow list operations.
     */
    public void cancel() {
        if (m_listWorkflowsWorker.get() != null) {
            m_listWorkflowsWorker.get().cancel(true);
            m_listWorkflowsWorker.set(null);
        }
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
     * @return the panel containing all controls
     */
    public JComponent getPanel() {
        return m_controls.m_panel;
    }

    /**
     * @param serverConnection the serverConnection to set
     */
    public void setServerConnection(final IServerConnection serverConnection) {
        m_serverConnection = Optional.ofNullable(serverConnection);
        enableAllUIElements(m_serverConnection.isPresent());
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
    private static Frame getParentFrame(final JPanel nodeDialogPanel) {
        Frame frame = null;
        var container = nodeDialogPanel.getParent();
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