/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 16, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow.local;

import java.awt.Color;
import java.awt.Dimension;
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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.json.util.JSONUtil;

import com.knime.explorer.nodes.callworkflow.IWorkflowBackend;
import com.knime.explorer.nodes.callworkflow.JSONInputPanel;

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
                updatePanels();
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
        p.add(m_collapsablePanels, gbc);

        p.setMinimumSize(new Dimension(650, 300));
        p.setPreferredSize(new Dimension(650, 300));
        addTab("Workflow", p);
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
        Map<String, String> parameterToJsonColumnMap = new LinkedHashMap<>();
        Map<String, ExternalNodeData> parameterToJsonConfigMap = new LinkedHashMap<>();
        for (Map.Entry<String, JSONInputPanel> entry : m_panelMap.entrySet()) {
            final String key = entry.getKey();
            final JSONInputPanel p = entry.getValue();
            final String jsonColumn = p.getJSONColumn();
            if (jsonColumn != null) {
                parameterToJsonColumnMap.put(key, jsonColumn);
            } else {
                try {
                    parameterToJsonConfigMap.put(key,
                        ExternalNodeData.builder(key).jsonValue(JSONUtil.parseJSONValue(p.getJSONConfig())).build());
                } catch (IOException ex) {
                    throw new InvalidSettingsException("No valid JSON for key " + key + ": " + ex.getMessage(), ex);
                }
            }
        }
        m_settings.setParameterToJsonColumnMap(parameterToJsonColumnMap);
        m_settings.setParameterToJsonConfigMap(parameterToJsonConfigMap);
        m_settings.save(settings);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final DataTableSpec[] specs)
        throws NotConfigurableException {
        m_settings.loadInDialog(settings);
        m_spec = specs[0];

        m_workflowPath.setSelectedItem(m_settings.getWorkflowPath());
        updatePanels();

        for (Map.Entry<String, ExternalNodeData> entry : m_settings.getParameterToJsonConfigMap().entrySet()) {
            JSONInputPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(m_spec, entry.getValue().getJSONValue(), null);
            }
        }

        for (Map.Entry<String, String> entry : m_settings.getParameterToJsonColumnMap().entrySet()) {
            JSONInputPanel p = m_panelMap.get(entry.getKey());
            if (p != null) {
                p.update(specs[0], null, entry.getValue());
            }
        }
    }

    private void updatePanels() {
        m_collapsablePanels.removeAll();
        m_panelMap.clear();
        m_errorLabel.setText(" ");
        if (StringUtils.isEmpty((CharSequence)m_workflowPath.getSelectedItem())) {
            m_errorLabel.setText("No workflow path provided");
        } else {
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
        getPanel().revalidate();
    }

    private IWorkflowBackend newBackend() throws Exception {
        final String workflowPath = (String)m_workflowPath.getSelectedItem();
        return LocalWorkflowBackend.newInstance(workflowPath, getNodeContext().getWorkflowManager().getContext());
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
