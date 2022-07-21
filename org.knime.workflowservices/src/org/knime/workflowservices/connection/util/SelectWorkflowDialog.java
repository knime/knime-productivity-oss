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
 *   Created on 26 Nov 2021 by carlwitt
 */
package org.knime.workflowservices.connection.util;

import java.awt.CardLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import org.knime.core.node.util.FilterableListModel;

/**
 * Dialog that enables the user to select a workflow from an already populated list. Offers a text field which can be
 * used as a filter. When something has been entered in the field only workflow paths containing this entry will be
 * shown in the list.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
public final class SelectWorkflowDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    enum State {
            /** State before triggering the first load. */
            UNINITIALIZED,
            /** When waiting for the list of workflow paths to be fetched. */
            LOADING,
            /**
             * Workflow paths could not be loaded or other exception happened. Hide all controls except for a refresh
             * button. See {@link DialogSelectWorkflow#setErrorText(String)}.
             */
            FAILURE,
            /** Workflows are listed and can be selected. See {@link DialogSelectWorkflow#setWorkflowPaths(List)}. */
            READY
    }

    /**
     * What to do with the selected list entry (an absolute workflow path) when closing the dialog. Fills the workflow
     * path input on the Call Workflow Service node dialog.
     */
    private final transient Consumer<String> m_setSelectedWorkflowPath;

    /**
     * Not intended to be used with threads, just a no arguments, no returns callback that will eventually call
     * {@link #setWorkflowPaths(List)} on this instance.
     *
     * Run in {@link #refreshWorkflowList()}.
     */
    private final transient Runnable m_refreshWorkflows;

    /** Shows either a loading message or the dialog to select a workflow path. */
    private final CardLayout m_cardLayout = new CardLayout();

    /** Displays an error message when fetching the workflow paths is not possible. */
    private final JLabel m_errorLabel = new JLabel();

    private final JTextField m_filterField = new JTextField();

    private final LoadingPanel m_panelLoading =
        new LoadingPanel("Fetching available workflows. This may take a while.");

    private State m_currentState = State.LOADING;

    /** Contents of the list view {@link #m_workflowList}. */
    private FilterableListModel m_filteredWorkflowModel = new FilterableListModel(Collections.emptyList());

    /** All workflows found in the Local Workspace or on the KNIME Server, if connected. */
    private JList<String> m_workflowList = new JList<>(m_filteredWorkflowModel);

    /**
     * @param frame parent dialog window
     * @param setSelectedWorkflowPath where to pass the selected list element (an absolute path string)
     * @param refreshWorkflowList what to do in {@link #refreshWorkflowList()}
     */
    public SelectWorkflowDialog(final Frame frame, final Consumer<String> setSelectedWorkflowPath,
        final Runnable refreshWorkflowList) {
        super(frame);

        m_setSelectedWorkflowPath = setSelectedWorkflowPath;
        m_refreshWorkflows = refreshWorkflowList;

        setTitle("Browse workflows");

        requestFocus();
        setModal(true);
        setLayout(m_cardLayout);

        // ready state, workflow path selector dialog is usable
        add(createContentPanel(), State.READY.name());

        // loading state, shown when workflow paths are loading
        add(m_panelLoading.getPanel(), State.LOADING.name());

        // error state
        add(createErrorPanel(), State.FAILURE.name());

        add(new JLabel(), State.UNINITIALIZED.name());

        // show loading panel
        setState(State.UNINITIALIZED);

        pack();
        setLocationRelativeTo(getParent());
        setSize(500, 800);

        getRootPane().registerKeyboardAction(e -> closeDialog(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Fill the list with workflow paths to display.
     *
     * @param paths to show
     */
    public void setWorkflowPaths(final List<String> paths) {
        m_filteredWorkflowModel = new FilterableListModel(paths);
        m_workflowList.setModel(m_filteredWorkflowModel);
        if (m_filteredWorkflowModel.getSize() > 0) {
            m_workflowList.setSelectedIndex(0);
        }
        setState(State.READY);
    }

    /** Remove all entries from the workflow path list and set state to {@link State#LOADING}. */
    public void clearWorkflowList() {
        if (m_currentState == State.READY) {
            m_workflowList.setModel(new FilterableListModel(Collections.emptyList()));
            setState(State.LOADING);
        }
    }

    /** Fetch all workflows from the remote side. Call {@link #setWorkflowPaths(List)} when done. */
    public void refreshWorkflowList() {
        setState(State.LOADING);
        m_refreshWorkflows.run();
    }

    /**
     * Prevents further user interaction with the dialog and shows the given error message.
     * @param message to display
     */
    public void setErrorText(final String message) {
        if(! "".equals(message)) {
            m_errorLabel.setText(message);
            setState(State.FAILURE);
        }
    }

    /**
     * @param state Update the dialog to reflect this operation or state.
     */
    synchronized void setState(final State state) {
        m_cardLayout.show(getContentPane(), state.name());
        m_currentState = state;
    }

    synchronized State getState() {
        return m_currentState;
    }

    private JPanel createContentPanel() {
        var selectWorkflowPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

        // Filter text field
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        createFilterField(selectWorkflowPanel, gbc);

        // Scroll pane with all available workflows
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        createWorkflowList(selectWorkflowPanel, gbc);

        // panel with select and cancel button
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        createControlPanel(selectWorkflowPanel, gbc);

        return selectWorkflowPanel;
    }

    private JPanel createErrorPanel() {
        var errorPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();

        errorPanel.add(m_errorLabel, gbc);

        var refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(l -> refreshWorkflowList());

        gbc.gridx++;
        errorPanel.add(refreshButton, gbc);

        return errorPanel;
    }

    /**
     * @param gbc
     * @param selectWorkflowPanel
     */
    private void createControlPanel(final JPanel selectWorkflowPanel, final GridBagConstraints gbc) {
        var selectButton = new JButton("Select");
        selectButton.addActionListener(l -> setSelectionAndClose());

        var cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(l -> closeDialog());

        var refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(l -> refreshWorkflowList());

        var controlPanel = new JPanel();

        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.LINE_AXIS));
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(selectButton, null);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(cancelButton, null);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(refreshButton, null);
        controlPanel.add(Box.createHorizontalStrut(5));
        controlPanel.add(Box.createHorizontalGlue());

        selectWorkflowPanel.add(controlPanel, gbc);
    }

    /**
     * Scroll pane with all available workflows
     *
     * @param selectWorkflowPanel
     * @param gbc
     */
    private void createWorkflowList(final JPanel selectWorkflowPanel, final GridBagConstraints gbc) {
        final var scrollPane = new JScrollPane(m_workflowList);
        selectWorkflowPanel.add(scrollPane, gbc);

        // double click on entry -> select and close
        m_workflowList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    setSelectionAndClose();
                }
            }
        });

        // press enter on entry -> select and close
        m_workflowList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    setSelectionAndClose();
                }
            }
        });
    }

    /**
     * Filter text field
     *
     * @param selectWorkflowPanel
     * @param gbc
     */
    private void createFilterField(final JPanel selectWorkflowPanel, final GridBagConstraints gbc) {

        selectWorkflowPanel.add(new JLabel("Search:"), gbc);

        m_filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(final DocumentEvent e) {
                updateFilterList(e);
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                updateFilterList(e);
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                updateFilterList(e);
            }
        });

        m_filterField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    m_workflowList.grabFocus();
                }
            }
        });
        gbc.gridx++;
        gbc.weightx = 1;
        selectWorkflowPanel.add(m_filterField, gbc);
        m_filterField.requestFocusInWindow();
    }

    /**
     * Listener for typing in the workflow path filter text field -> shows only workflow paths that contain the search
     * string.
     *
     * @param e
     */
    private void updateFilterList(final DocumentEvent e) {
        try {
            final var doc = e.getDocument();
            m_filteredWorkflowModel.setFilter(doc.getText(0, doc.getLength()));
        } catch (BadLocationException e1) { // NOSONAR
            // Will never happen
        }
    }

    /** Close dialog, return selected value. */
    private void setSelectionAndClose() {
        String selectedWorkflow = m_workflowList.getSelectedValue();
        m_setSelectedWorkflowPath.accept(selectedWorkflow);
        closeDialog();
    }

    /** Only close dialog. */
    private void closeDialog() {
        m_filteredWorkflowModel.setFilter("");
        setVisible(false);
        dispose();
    }

    /**
     * Show the dialog. Trigger initial fetching of remote workflows if not initialized yet.
     */
    public void open() {
        if (getState() == SelectWorkflowDialog.State.UNINITIALIZED) {
            // calls fetchRemoteWorkflows() and sets internal state of the dialog to LOADING
            refreshWorkflowList();
        }
        requestFocus();
        m_filterField.setText("");
        m_filterField.grabFocus();
        setVisible(true);
    }

}