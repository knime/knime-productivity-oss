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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

import org.knime.workflowservices.connection.util.LoadingPanel;

/**
 * Allows to show a dialog for selecting from a list of deployments. The user may filter deployments by team name or
 * execution context name.
 *
 * The operations on the dialog are as follows:
 *
 * <li>Open the dialog. Initially, the dialog is in loading state.
 *
 * <li>Set selected deployment. To restore the persisted state, the selected deployment can be set. This selects the
 * corresponding row in the deployment selection table, if any.
 *
 * <li>Set deployments {@link #setDeployments(List)}. This will set the state to ready if in loading state. Every time
 * deployments are set, the deployment selection is restored if possible or cleared if no such deployment exists.
 * {@link #setSelectedDeploymentById(String)}
 *
 * <li>Add deployment selection listener. The listener is informed when the user confirms the selection by pressing
 * accept or double clicking an entry in the table. {@link #addDeploymentChangedListener(PropertyChangeListener)}
 *
 * <li>Add a refresh listener. The user may click a button to refresh the list of deployments. This will disable the
 * user interface and invoke the callback(s) passed to {@link #addRefreshListener(Runnable)}. The callback is
 * responsible for calling {@link #setDeployments(List)}.
 *
 * <h2>Filtering deployments</h2>
 *
 * The user may filter the list of deployments by team name or execution context name. The filter criterion is selected
 * from a combo box. The filter options are selected from a combo box that is populated with the options available for
 * the selected filter criterion. The selected filter is not persisted but remains active if the deployments are
 * refreshed.
 *
 * <h2>Life Cycle</h2>
 *
 * The dialog components are created once. A new dialog instance is created every time {@link #open()} is invoked and
 * disposed when the user closes the dialog.
 *
 * <h2>Listeners</h2>
 * <li>Table selection changed -> Enable select button iff a row is selected
 * <li>Select button pressed -> Notify listeners about the new selection, close dialog
 * <li>Table row double clicked -> Same as for select button pressed
 * <li>Refresh button pressed -> Notify registered refresh listeners
 * <li>Escape button pressed -> Close dialog, discard selection
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
@SuppressWarnings("javadoc")
class DeploymentSelectionDialogManager {

    /** Initial size of the dialog when opening it. */
    private static final Dimension DEFAULT_SIZE = new Dimension(920, 620);

    /**
     * The columns shown in the deployment table. Each column has a label, a preferred width, and a function that
     * extracts the value from a deployment.
     */
    private enum Column {
            NAME("Name", Deployment::name), //
            WORKFLOW("Workflow", Deployment::workflowPath), //
            TEAM_NAME("Team", Deployment::teamName), //
            EXECUTION_CONTEXT_NAME("Execution Context", Deployment::executionContextName);

        private final String m_label;

        private final Function<Deployment, String> m_accessor;

        Column(final String label, final Function<Deployment, String> accessor) {
            this.m_label = label;
            this.m_accessor = accessor;
        }

        private static class TableModel extends DefaultTableModel {
            private static final long serialVersionUID = 1L;

            /** The deployments matching the filter criterion. */
            transient List<Deployment> m_deployments = new ArrayList<>();

            TableModel() {
                super(new Object[][]{}, Arrays.stream(values()).map(v -> v.m_label).toArray(String[]::new));
            }

            void addRow(final Deployment deployment) {
                addRow(Arrays.stream(Column.values()).map(c -> c.m_accessor.apply(deployment)).toArray());
                m_deployments.add(deployment);
            }

            @Override
            public boolean isCellEditable(final int row, final int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(final int columnIndex) {
                // if other sorting is needed for a column, return something different
                return String.class;
            }

            /**
             * @param modelRow
             * @return
             */
            Deployment getDeployment(final int modelRow) {
                return m_deployments.get(modelRow);
            }

            void clear() {
                setRowCount(0);
                m_deployments.clear();
            }

            List<Deployment> getDeployments(){
                return m_deployments;
            }
        }
    }

    /**
     * The user may show all deployments or filter them by team name or execution context name.
     */
    private enum FilterCriterion {
            ALL("No Filter", null), //
            TEAM_NAME("Team", Deployment::teamName), //
            EXECUTION_CONTEXT_NAME("Execution Context", Deployment::executionContextName);

        private final String m_label;

        // never returns null
        private final Function<Deployment, String> m_accessor;

        FilterCriterion(final String label, final Function<Deployment, String> accessor) {
            this.m_label = label;
            this.m_accessor =
                deployment -> accessor.apply(deployment) == null ? "<No value>" : accessor.apply(deployment);
        }

        Predicate<Deployment> getFilter(final String filterOption) {
            if (this == ALL) {
                return deployment -> true;
            }
            return deployment -> m_accessor.apply(deployment).equals(filterOption);
        }

        private static final DefaultListCellRenderer RENDERER = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
                final boolean isSelected, final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FilterCriterion filterCriterion) {
                    setText(filterCriterion.m_label);
                }
                return this;
            }
        };
    }

    private class DeploymentSelectionDialog extends JDialog {

        /** The dialog can be in these two states. */
        private enum Status {
                /**
                 * Controls are disabled, loading indicator is shown. Initial state. State after clicking the refresh
                 * button.
                 */
                LOADING,
                /**
                 * Controls are enabled. State after {@link DeploymentSelectionDialogManager#setDeployments(List)} is
                 * called.
                 */
                READY;
        }

        private static final long serialVersionUID = 1L;

        CardLayout cardLayout = new CardLayout();
         JPanel main;

        /**
         * @param parent the component that determines the relative position of this dialog (e.g., the screen it shows up on)
         */
        public DeploymentSelectionDialog(final Component parent) {
            super(SwingUtilities.getWindowAncestor(parent));

            setTitle("Select Deployment");

            requestFocus();

            main = new JPanel(cardLayout);
            main.add(createReadyPanel(), Status.READY.name());
            main.add(new LoadingPanel("Fetching Deployments...").getPanel(), Status.LOADING.name());
            setState(Status.LOADING);
            // ready state, workflow path selector dialog is usable
            add(main, BorderLayout.CENTER);

            pack();
            setPreferredSize(DEFAULT_SIZE);

            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(m_parent);
            setResizable(true);

            // close on ESC
            getRootPane().registerKeyboardAction(e -> escapeKeyPressed(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

            setVisible(true);
        }

        private void close() {
            setVisible(false);
            dispose();
            m_dialog = Optional.empty();
            m_selectedDeployment = Optional.empty();
        }
        private void setState(final Status state) {
            cardLayout.show(main, state.name());
        }

        private JPanel createReadyPanel() {
            var ready = new JPanel();
            // label the filter criterion combo box
            m_filterCriterionLabel.setLabelFor(m_filterCriterionComboBox);

            JScrollPane tableScrollPane = new JScrollPane(m_deploymentsTable);
            m_deploymentsTable.setFillsViewportHeight(true);
            tableScrollPane.setPreferredSize(DEFAULT_SIZE);
            tableScrollPane.setMinimumSize(DEFAULT_SIZE);

            ready.setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 0;
            c.weighty = 0;
            c.insets = new Insets(5, 5, 5, 5);
            ready.add(m_filterCriterionLabel, c);
            c.gridx = 1;
            c.weightx = 0.000001;
            ready.add(m_filterCriterionComboBox, c);
            // the filter option combo box gets all available space
            c.weightx = 1;
            c.gridx = 2;
            ready.add(m_filterComboBox, c);
            c.weightx = 0;
            c.gridx = 3;
            ready.add(m_refreshButton, c);
            c.gridx = 0;
            c.gridy = 1;
            c.gridwidth = 4;
            ready.add(tableScrollPane, c);
            c.gridx = 0;
            c.gridy = 2;
            c.gridwidth = 4;
            ready.add(m_accept, c);
            return ready;
        }

    }

    // data

    List<Deployment> m_deployments = List.of();

    // listeners

    PropertyChangeSupport m_eventSupport = new PropertyChangeSupport(this);

    // state

    /**
     * Set via {@link #setSelectedDeploymentById(String)} to restore state. Once deployments are set via
     * {@link #setDeployments(List)}, this is resolved to a Deployment and {@link #m_initiallySelectedDeployment} is
     * set.
     */
    private Optional<String> m_initiallySelectedDeploymentId = Optional.empty();

    /**
     * The restored selection. Empty if nothing has been selected previously or if the previously selected deployment
     * does not exist anymore. Upon close, listeners are only notified if the selected deployment is not the initially
     * selected deployment.
     */
    private Optional<Deployment> m_initiallySelectedDeployment = Optional.empty();

    /**
     * This is kept in sync with the currently selected row in the deployments table.
     *
     * Upon close, listeners are only notified if the selected deployment is not the initially
     * selected deployment.
     */
    private Optional<Deployment> m_selectedDeployment = Optional.empty();

    // controls

    // position the dialog relative to this component
    private final Component m_parent;

    // the currently open dialog, if any
    private Optional<DeploymentSelectionDialog> m_dialog = Optional.empty();

    // ui elements shown in every created dialog instance

    private final JTable m_deploymentsTable = new JTable(new Column.TableModel());

    // label for the filter criterion combo box
    private final JLabel m_filterCriterionLabel = new JLabel("Filter Deployments:");

    // the selected filter criterion
    private final JComboBox<FilterCriterion> m_filterCriterionComboBox = createFilterCriterionComboBox();

    // the selected filter option
    private final JComboBox<String> m_filterComboBox = createFilterComboBox();

    private final JButton m_refreshButton = new JButton("Refresh");

    // closes the dialog. Enabled iff an element in the table is selected.
    private final JButton m_accept = new JButton("Select");

    // ------------------- Constructor -------------------

    DeploymentSelectionDialogManager(final Component parent) {
        m_parent = parent;

        m_deploymentsTable.setFillsViewportHeight(true);

        m_deploymentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        m_deploymentsTable.setAutoCreateRowSorter(true);

        m_deploymentsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    tableRowDoubleClicked();
                }
            }
        });

        m_deploymentsTable.getSelectionModel().addListSelectionListener(e -> {
            if(e.getValueIsAdjusting()) {
                return;
            }
            tableSelectionChanged();
        });

        m_deploymentsTable.setRowHeight(20);

        m_accept.addActionListener(l -> selectButtonPressed());

        updateFilterComboBox(getCurrentFilterCriterion());

    }

    // ------------------- External operations -------------------

    /**
     * Shows the deployment selection dialog as a modal dialog.
     */
    void open() {
        var dialog = new DeploymentSelectionDialog(m_parent);
        m_dialog = Optional.of(dialog);
        if(m_deployments != null) {
            // when re-opening the dialog, set the ready state right away.
            setDeployments(m_deployments);
        }
    }

    /**
     * @return the selected deployment or an empty optional if no deployment is selected.
     */
    private Optional<Deployment> getTableSelectionAsDeployment() {
        int selectedRow = m_deploymentsTable.getSelectedRow();
        if (selectedRow == -1) {
            return Optional.empty();
        }
        // translate sorting
        int modelRow = m_deploymentsTable.convertRowIndexToModel(selectedRow);
        return Optional.of(((Column.TableModel)m_deploymentsTable.getModel()).getDeployment(modelRow));
    }

    /**
     * When the selected row in the deployments table changes, notify all listeners.
     *
     * @param listener notified every time the selected deployment is changed.
     */
    void addDeploymentChangedListener(final PropertyChangeListener listener) {
        m_eventSupport.addPropertyChangeListener(listener);
    }


    /**
     * @param listener to run when the user chooses to refresh the deployments. The callback is supposed to eventually
     *            call {@link #setDeployments(List)}.
     */
    void addRefreshListener(final Runnable listener) {
        m_refreshButton.addActionListener(e -> {
            // disable all controls to avoid concurrent invocations of the call back
            m_dialog.ifPresent(dlg -> dlg.setState(DeploymentSelectionDialog.Status.LOADING));
            listener.run();
        });
    }

    /**
     * Updates the display to show the deployments that match the filter criterion, if any is selected.
     *
     * @param deployments the deployments available for selection
     */
    void setDeployments(final List<Deployment> deployments) {
        m_deployments = deployments;

        updateDeploymentTable();

        // in case we did a refresh, re-enable all controls now
        m_dialog.ifPresent(dlg -> dlg.setState(DeploymentSelectionDialog.Status.READY));
    }

    /**
     * Synchronized because this is called from table selection change listeners (gui thread) and the main thread.
     *
     * @param id the id of the deployment to select or null if nothing should be selected.
     */
    synchronized void setSelectedDeploymentById(final String id) {
        m_initiallySelectedDeploymentId = Optional.ofNullable(id);
        applySelection();
    }

    // ------------------- Listeners -------------------

    /** Notify listeners about the new selection, close dialog. */
    private void selectButtonPressed() {
        setSelectedDeployment(getTableSelectionAsDeployment().get()); // NOSONAR button is only enabled if value present
        m_dialog.get().close();
    }

    private void tableRowDoubleClicked() {
        selectButtonPressed();
    }

    /** Update selected deployment. Enable select button iff a row is selected. */
    private void tableSelectionChanged() {
        m_selectedDeployment = getTableSelectionAsDeployment();
        m_accept.setEnabled(m_selectedDeployment.isPresent());
    }

    /** Close dialog, discard selection */
    private void escapeKeyPressed() { // NOSONAR all listeners are gathered here
        m_dialog.get().close();
        m_selectedDeployment = Optional.empty();
    }

    // ------------------- Internal operations -------------------

    /**
     * Notifies listeners if the new deployment is different from the old deployment.
     * @param newDeployment new selected deployment
     */
    private synchronized void setSelectedDeployment(final Deployment newDeployment) {
        m_selectedDeployment = Optional.ofNullable(newDeployment);
        // notify listeners, if old and new are not equal
        m_eventSupport.firePropertyChange("deployment", m_initiallySelectedDeployment.orElse(null),
            m_selectedDeployment.orElse(null));
    }

    /** Select a currently visible (not filtered) row in the table according to {@link #m_initiallySelectedDeploymentId}. */
    private synchronized void applySelection() {
        OptionalInt selectedRowIdx = OptionalInt.empty();

        if (m_initiallySelectedDeploymentId.isPresent()) {
            var filteredDeployments = getTableModel().getDeployments();
            int row = DeploymentSelectionPanel.resolve(m_initiallySelectedDeploymentId.get(), filteredDeployments)//
                    .map(filteredDeployments::indexOf)//
                    .orElse(-1);
            if (row != -1) {
               selectedRowIdx = OptionalInt.of(m_deploymentsTable.convertRowIndexToView(row));
            }
        }
        selectedRowIdx.ifPresentOrElse(r -> m_deploymentsTable.setRowSelectionInterval(r, r),
            m_deploymentsTable::clearSelection);
    }



    /**
     * @return the currently active filter criterion. FilterCriterion.ALL if no filter is active.
     */
    private FilterCriterion getCurrentFilterCriterion() {
        return Optional.ofNullable((FilterCriterion)m_filterCriterionComboBox.getSelectedItem())
            .orElse(FilterCriterion.ALL);
    }

    /**
     * @return the deployments that match the currently selected filter criterion and filter option.
     */
    private List<Deployment> filteredDeployments() {
        // get the selected filter criterion
        final FilterCriterion filterCriterion = getCurrentFilterCriterion();

        // get the selected filter option
        final String filterValue = (String)m_filterComboBox.getSelectedItem();

        // filter the deployments by the selected team name
        return m_deployments.stream()//
            .filter(filterCriterion.getFilter(filterValue))//
            .toList();
    }


    /**
     * Updates the table model to show the deployments that match the selected filter criterion and filter option. Calls
     * {@link #applySelection()}.
     */
    private void updateDeploymentTable() {
        // update the table
        Column.TableModel model = getTableModel();
        model.clear();
        filteredDeployments().stream().forEach(model::addRow);
        applySelection();
    }

    /**
     * Updates the filter options (e.g., all team names or execution context names). If the filter criterion is "No
     * Filter", the combo box is hidden.
     *
     * @param selectedFilterCriterion the selected filter criterion
     */
    private void updateFilterComboBox(final FilterCriterion selectedFilterCriterion) {
        // remove old filter options
        m_filterComboBox.removeAllItems();

        if (selectedFilterCriterion != FilterCriterion.ALL) {
            // add the new filter options
            m_deployments.stream()//
                .map(selectedFilterCriterion.m_accessor)//
                .distinct()//
                .sorted()//
                .forEach(m_filterComboBox::addItem);
        }

        boolean hasFilterOptions = m_filterComboBox.getItemCount() > 0;
        if (hasFilterOptions) {
            // update deployment table
            m_filterComboBox.setSelectedIndex(0);
        } else {
            updateDeploymentTable();
        }
        m_filterComboBox.setVisible(hasFilterOptions);
    }

    private Column.TableModel getTableModel() {
        return (Column.TableModel)m_deploymentsTable.getModel();
    }

    // ------------------- Constructor helpers -------------------

    /**
     * @return a combo box with the filter criteria. When selecting a filter criterion, updateFilterComboBox() is
     *         called.
     */
    private JComboBox<FilterCriterion> createFilterCriterionComboBox() {
        // create a combo box with the filter criteria
        JComboBox<FilterCriterion> result = new JComboBox<>(FilterCriterion.values());
        result.setRenderer(FilterCriterion.RENDERER);

        result.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            // get the selected filter criterion
            FilterCriterion selectedFilterCriterion = (FilterCriterion)e.getItem();
            updateFilterComboBox(selectedFilterCriterion);
        });
        return result;
    }

    /**
     * @return a combo box that calls updateDeploymentTable() when the selected filter option changes.
     */
    private JComboBox<String> createFilterComboBox() {
        JComboBox<String> result = new JComboBox<>();
        result.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            updateDeploymentTable();
        });

        return result;
    }

}
