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
 *   Created on 12 Jan 2023 by Dionysios Stolis
 */
package org.knime.workflowservices;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.apache.commons.lang3.ObjectUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.connections.meta.FSType;
import org.knime.filehandling.core.util.GBCBuilder;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.ServerConnectionUtil;
import org.knime.workflowservices.connection.util.ConnectionUtil;

import com.google.common.collect.ImmutableList;

/**
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public class ExecutionContextSelector {

    private final JComboBox<ExecutionContext> m_executionContextsComboBox;

    private final JPanel m_executionContextSelectorPanel;

    private final JLabel m_errorLabel = new JLabel();

    private ExecutionContextWorker m_executionContextWorker;

    /**
     *
     */
    public ExecutionContextSelector() {
        m_executionContextsComboBox = new JComboBox<>();
        m_executionContextSelectorPanel = new JPanel();
    }

    /**
     * Creates a selection panel for execution contexts.
     *
     * @return the panel
     */
    public JPanel createSelectionPanel() {
        m_executionContextSelectorPanel.setLayout(new GridBagLayout());

        final var gbc = new GBCBuilder().resetX().resetY().anchorLineStart().fillHorizontal();

        m_executionContextsComboBox.setRenderer(new ExecutionContextListRenderer());
        m_executionContextsComboBox.setMaximumSize(m_executionContextsComboBox.getPreferredSize());
        m_executionContextSelectorPanel.add(new JLabel("Execution Context"), gbc.setWeightX(0).setWidth(1).build());
        m_executionContextSelectorPanel.add(m_executionContextsComboBox, gbc.incX().setWeightX(1).insetLeft(5).build());
        m_executionContextSelectorPanel.add(m_errorLabel, gbc.incY().build());
        m_errorLabel.setForeground(Color.RED.darker());
        m_errorLabel.setVisible(false);

        m_executionContextSelectorPanel.setVisible(false);
        return m_executionContextSelectorPanel;
    }

    private void fillExecutionContextsDropdown(final CallWorkflowConnectionConfiguration configuration) {
        m_executionContextsComboBox.setEnabled(false);
        m_executionContextsComboBox.removeAllItems();
        if (configuration == null) {
            return;
        }
        //close the worker
        close();
        m_executionContextWorker = new ExecutionContextWorker(configuration,
            list -> onExecutionContextsLoad(list, configuration), this::onFailure);
        m_executionContextWorker.execute();
    }

    private void onExecutionContextsLoad(final List<ExecutionContext> executionContextItemList,
        final CallWorkflowConnectionConfiguration configuration) {
        m_errorLabel.setVisible(false);

        //alphabetic order by title.
        var sortedExecutionContextList =
            ImmutableList.sortedCopyOf(Comparator.comparing(ExecutionContext::name), executionContextItemList);
        sortedExecutionContextList.stream().forEach(m_executionContextsComboBox::addItem);

        Optional<ExecutionContext> defaultExecutionContextItem =
            sortedExecutionContextList.stream().filter(ExecutionContext::isDefault).findFirst();

        //Set default execution context if there is not selected item.
        sortedExecutionContextList.stream().filter(exec -> exec.id().equals(configuration.getExecutionContext()))
            .findFirst().ifPresentOrElse(exec -> m_executionContextsComboBox.getModel().setSelectedItem(exec),
                () -> defaultExecutionContextItem
                    .ifPresent(d -> m_executionContextsComboBox.getModel().setSelectedItem(d)));
        m_executionContextsComboBox.setEnabled(true);
        m_executionContextSelectorPanel.setVisible(true);
    }

    private void onFailure(final String errorMessage) {
        m_errorLabel.setText(errorMessage);
        m_executionContextSelectorPanel.setVisible(true);
        m_errorLabel.setVisible(true);
    }

    /**
     *  Cancels the Execution context worker.
     */
    public void close() {
        if (ObjectUtils.isNotEmpty(m_executionContextWorker)) {
            m_executionContextWorker.cancel(true);
            m_executionContextWorker = null;
        }
    }

    /**
     * Loads the settings from the provided configuration and fetches available execution contexts.
     *
     * @param configuration the call workflow connection configuration.
     */
    public final void loadSettingsInDialog(final CallWorkflowConnectionConfiguration configuration) {
        var fsType = switch (configuration.getConnectionType()) {
            case FILE_SYSTEM -> configuration.getWorkflowChooserModel().getLocation().getFSType();
            case HUB_AUTHENTICATION -> FSType.HUB;
        };
        if (ConnectionUtil.isHubConnection(fsType)) {
            fillExecutionContextsDropdown(configuration);
        } else {
            m_executionContextSelectorPanel.setVisible(false);
        }
    }

    /**
     * Stores the execution context in the configuration.
     *
     * @param configuration the configuration
     */
    public void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        if (m_executionContextsComboBox != null) {
            var executionContextItem = (ExecutionContext)m_executionContextsComboBox.getSelectedItem();
            if (executionContextItem != null) {
                configuration.setExecutionContext(executionContextItem.id());
            }
        }
    }

    static class ExecutionContextWorker extends SwingWorkerWithContext<List<ExecutionContext>, Void> {

        private final CallWorkflowConnectionConfiguration m_configuration;

        private final Consumer<List<ExecutionContext>> m_comboBoxAdjuster;

        private final Consumer<String> m_errorDisplay;

        ExecutionContextWorker(final CallWorkflowConnectionConfiguration configuration,
            final Consumer<List<ExecutionContext>> comboBoxAdjuster, final Consumer<String> errorDisplay) {
            m_configuration = configuration;
            m_comboBoxAdjuster = comboBoxAdjuster;
            m_errorDisplay = errorDisplay;
        }

        /**
         * the map is not empty {@inheritDoc}
         */
        @Override
        protected List<ExecutionContext> doInBackgroundWithContext() throws Exception {
            var callWorkflowConnection = ConnectionUtil.createConnection(m_configuration).orElseThrow(
                () -> new InvalidSettingsException(String.format("Can not create the workflow execution connection for the workflow path '%s'",
                    m_configuration.getWorkflowChooserModel().getLocation().getPath())));
            return callWorkflowConnection.getExecutionContexts();
        }

        @Override
        protected void doneWithContext() {
            if (!isCancelled()) {
                try {
                    var excecutionContextMap = get();
                    if (excecutionContextMap != null) {
                        m_comboBoxAdjuster.accept(excecutionContextMap);
                    }
                } catch (InterruptedException | CancellationException e) {
                    NodeLogger.getLogger(getClass()).warn(e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    m_errorDisplay.accept(ServerConnectionUtil.handle(e).getLeft());
                }
            }
        }
    }

    static class ExecutionContextListRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 4433786956198906420L;

        @Override
        public Component getListCellRendererComponent(final JList<?> list, Object value, final int index,
            final boolean isSelected, final boolean cellHasFocus) {
            if (value instanceof ExecutionContext) {
                value = ((ExecutionContext)value).name();
            }
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            return this;
        }
    }

}
