/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Feb 16, 2015 by wiswedel
 */
package org.knime.explorer.nodes.callworkflow.local;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.util.VerticalCollapsablePanels;
import org.knime.core.node.util.ViewUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.productivity.base.callworkflow.JSONInputPanel;

/**
 * Dialog to node.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class CallLocalWorkflowNodeDialogPane extends NodeDialogPane {
    private final JComboBox<String> m_workflowPath;

    private final JLabel m_errorLabel;

    private final VerticalCollapsablePanels m_collapsablePanels;

    private final Map<String, JSONInputPanel> m_panelMap;

    private final JCheckBox m_createReportChecker;

    private final JComboBox<RptOutputFormat> m_reportFormatCombo;

    private final JProgressBar m_loadingAnnimation;

    private final JLabel m_loadingMessage;

    private AtomicBoolean m_localWorkflowCalled = new AtomicBoolean(false);

    private DataTableSpec m_spec;

    private final CallLocalWorkflowConfiguration m_settings = new CallLocalWorkflowConfiguration();

    CallLocalWorkflowNodeDialogPane() {
        final JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        m_errorLabel = new JLabel();
        m_errorLabel.setForeground(Color.RED.darker());
        m_workflowPath = new JComboBox<>();
        m_workflowPath.setEditable(true);
        m_workflowPath.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(final ItemEvent e) {
                clearInputPanel(p);
            }
        });

        m_workflowPath.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void keyTyped(final KeyEvent e) {
                clearInputPanel(p);
            }
        });
        fillWorkflowList();

        m_createReportChecker = new JCheckBox("Create Report");
        m_reportFormatCombo = new JComboBox<>(ArrayUtils.removeElement(RptOutputFormat.values(), RptOutputFormat.HTML));
        m_createReportChecker.addItemListener(e -> m_reportFormatCombo.setEnabled(m_createReportChecker.isSelected()));
        m_createReportChecker.doClick();

        m_collapsablePanels = new VerticalCollapsablePanels();
        m_panelMap = new LinkedHashMap<>();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.weightx = 1.0;
        gbc.gridx = gbc.gridy = 0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        p.add(new JLabel("Workflow Path: "), gbc);
        gbc.gridx += 1;
        gbc.weightx = 1.0;
        p.add(m_workflowPath, gbc);

        JButton b = new JButton("Load input format");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                updatePanels(false);
            }
        });
        gbc.gridx++;
        gbc.weightx = 0;
        p.add(b, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy += 1;
        p.add(m_errorLabel, gbc);

        gbc.gridy += 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        p.add(ViewUtils.getInFlowLayout(m_createReportChecker, m_reportFormatCombo), gbc);


        JPanel loadingBox = new JPanel();
        loadingBox.setLayout(new BoxLayout(loadingBox, BoxLayout.PAGE_AXIS));

        m_loadingMessage = new JLabel();
        m_loadingMessage.setForeground(Color.RED.darker());
        m_loadingMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
        m_loadingAnnimation = new JProgressBar();
        m_loadingAnnimation.setIndeterminate(true);
        m_loadingAnnimation.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingBox.add(m_loadingAnnimation);
        loadingBox.add(m_loadingMessage);

        gbc.gridy += 1;
        gbc.fill = GridBagConstraints.NONE;
        p.add(loadingBox, gbc);

        gbc.fill = GridBagConstraints.BOTH;
        p.add(m_collapsablePanels, gbc);

        addTab("Workflow", new JScrollPane(p));
    }

    private void fillWorkflowList() {
        m_errorLabel.setText("");
        SwingWorkerWithContext<List<String>, Void> worker = new SwingWorkerWithContext<List<String>, Void>() {
            @Override
            protected List<String> doInBackgroundWithContext() throws Exception {
                return listWorkflows();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void doneWithContext() {
                try {
                    m_workflowPath.removeAllItems();
                    for (String s : get()) {
                        m_workflowPath.addItem(s);
                    }
                    m_workflowPath.setSelectedItem(m_settings.getWorkflowPath());
                } catch (ExecutionException ex) {
                    Throwable cause = (ex.getCause() != null) ? ExceptionUtils.getRootCause(ex) : ex;
                    String message = "Could not list workflows: " + cause.getMessage();
                    m_errorLabel.setText(message);
                    NodeLogger.getLogger(CallLocalWorkflowNodeDialogPane.class).error(message, cause);
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }

        };
        worker.execute();
    }

    /** {@inheritDoc} */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        m_settings.setWorkflowPath((String) m_workflowPath.getSelectedItem());

        RptOutputFormat reportFormatOrNull;
        if (m_createReportChecker.isSelected()) {
            reportFormatOrNull = (RptOutputFormat)m_reportFormatCombo.getSelectedItem();
        } else {
            reportFormatOrNull = null;
        }
        JSONInputPanel.saveSettingsTo(m_settings, m_panelMap.entrySet());
        m_settings.setReportFormatOrNull(reportFormatOrNull);
        m_settings.save(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {
        m_settings.loadInDialog(settings);
        m_spec = specs[0];

        m_workflowPath.setSelectedItem(m_settings.getWorkflowPath());
        RptOutputFormat reportFormatOrNull = m_settings.getReportFormatOrNull();
        if ((reportFormatOrNull != null) != m_createReportChecker.isSelected()) {
            m_createReportChecker.doClick();
        }
        m_reportFormatCombo.setSelectedItem(reportFormatOrNull != null
                ? reportFormatOrNull : m_reportFormatCombo.getModel().getElementAt(0));

        updatePanels(true);
    }

    /**
     * Update the collapsable panels in the view with input nodes of the workflow to call specifying
     * if the panels should be additionally updated with previously saved settings.
     *
     * @param updateFromSavedSettings whether to update the panels with saved settings
     */
    private void updatePanels(final boolean updateFromSavedSettings) {

        // Attempt to load the workflow only once, therefore we wait with the update
        // (and try to load the workflow again) until the SwingWorker is done.
        if(m_localWorkflowCalled.compareAndSet(true, true)){
            return;
        }

        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");

        // Display this message by default until a workflow could be successfully loaded.
        m_loadingMessage.setText("Can't access specified workflow (may be executing)");
        m_loadingAnnimation.setVisible(true);
        m_collapsablePanels.setVisible(false);

        SwingWorker<Void, Void> panelUpdater = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {

                // Current context needs to be available in SwingWorker thread for
                // KNIME URL resolution when the workflow is loaded.
                NodeContext.pushContext(getNodeContext());

                if (StringUtils.isEmpty((CharSequence)m_workflowPath.getSelectedItem())) {
                    m_errorLabel.setText("No workflow path provided");
                } else {

                    m_localWorkflowCalled.set(true);

                    try (IWorkflowBackend backend = newBackend()) {
                        Map<String, ExternalNodeData> inputNodes = backend.getInputNodes();
                        for (Map.Entry<String, ExternalNodeData> entry : inputNodes.entrySet()) {
                            JSONInputPanel p = new JSONInputPanel(entry.getValue().getJSONValue(), m_spec);
                            m_panelMap.put(entry.getKey(), p);
                            m_collapsablePanels.addPanel(p, false, entry.getKey());
                        }
                    } catch (Exception e) {
                        NodeLogger.getLogger(getClass()).debug(e.getMessage(), e);
                        m_errorLabel.setText(e.getMessage());
                    }
                }
                return null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void done() {
                m_localWorkflowCalled.set(false);
                m_loadingMessage.setText(" ");
                m_loadingAnnimation.setVisible(false);
                m_collapsablePanels.setVisible(true);

                if(updateFromSavedSettings){
                    JSONInputPanel.loadSettingsFrom(m_settings, m_panelMap, m_spec);
                }
            }
        };
        panelUpdater.execute();

        JPanel panel = getPanel();
        // some weird sequence to force the UI to properly update, see AP-6191
        panel.invalidate();
        panel.revalidate();
        panel.repaint();
    }

    private IWorkflowBackend newBackend() throws Exception {
        final String workflowPath = (String)m_workflowPath.getSelectedItem();
        return LocalWorkflowBackend.newInstance(workflowPath, getNodeContext().getWorkflowManager());
    }

    /** {@inheritDoc} */
    @Override
    public void onClose() {
        m_spec = null;
    }


    private static final Comparator<String> WORKFLOW_PATH_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(final String o1, final String o2) {
            int depth1 = StringUtils.countMatches(o1, "/");
            int depth2 = StringUtils.countMatches(o2, "/");

            if (depth1 < depth2) {
                return -1;
            } else if (depth1 > depth2) {
                return 1;
            } else {
                return o1.compareTo(o2);
            }
        }
    };

    private void clearInputPanel(final JPanel p) {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");
        m_collapsablePanels.revalidate();
        p.revalidate();
        getPanel().repaint();
    }

    List<String> listWorkflows() throws IOException {
        final Path root = getNodeContext().getWorkflowManager().getContext().getMountpointRoot().toPath();

        final List<String> workflows = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            /**
             * {@inheritDoc}
             */
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                throws IOException {
                if (dir.getFileName().toString().equals(".metadata")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Path workflowFile = dir.resolve(WorkflowPersistor.WORKFLOW_FILE);
                Path templateFile = dir.resolve(WorkflowPersistor.TEMPLATE_FILE);

                if (Files.exists(workflowFile)) {
                    if (!Files.exists(templateFile)) {
                        workflows.add("/" + root.relativize(dir).toString());
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return super.preVisitDirectory(dir, attrs);
                }
            }
        });

        Collections.sort(workflows, WORKFLOW_PATH_COMPARATOR);
        return workflows;
    }
}
