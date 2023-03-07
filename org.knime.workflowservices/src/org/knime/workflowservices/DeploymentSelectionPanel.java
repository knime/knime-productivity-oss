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
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.knime.core.node.FlowVariableModel;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.DeploymentExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * A panel that shows the name of a selected deployment. Provides a button to select from a list of deployments using a
 * {@link DeploymentSelectionDialogManager}.
 *
 * <li>When {@link #loadSettings(NodeSettingsRO)} is called, only the id of the selected deployment is shown.
 * <li>As soon as {@link #setDeployments(List)} is called, the id is resolved to the actual name and an error is shown if
 * that deployment does not exist anymore.
 *
 * <h2>Operations</h2>
 *
 * The operations on the panel are as follows:
 *
 * <li>Get the controls. All controls to be embedded into another dialog. {@link #getPanel()}
 *
 * <li>Add a listener that is called every time the selected deployment changes.
 * {@link #addDeploymentChangedListener(Consumer)}
 *
 * <li>Set deployments. {@link #setDeployments(List)}
 *
 * <li>Set the selected deployment. To restore the persisted state, the selected deployment can be set. If the selected
 * deployment is not in the list of deployments, no deployment is selected. {@link #setSelectedDeploymentById(String)}
 *
 * <li>Get the selected deployment. None may be selected (e.g., if there are none available)
 * {@link #getSelectedDeployment()}.
 *
 * <li>Set an error message that will be displayed at the bottom of the panel. {@link #setErrorMessage(String)}
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
@SuppressWarnings("javadoc")
class DeploymentSelectionPanel {

    /** The panel has the following life cycle. */
    private enum Status {
            /** If the deployment id is overwritten by a flow variable, the controls are hidden. */
            CONTROLLED_BY_FLOW_VARIABLE,
            /**
             * A deployment selection has been set (either no deployment or a restored selection) but not validated yet.
             * Changes as soon as {@link DeploymentSelectionPanel#setDeployments(List)} is called which validates the
             * selection.
             */
            VALIDATION_PENDING,
            /** No deployment is selected or the selected deployment does not exist anymore. */
            INVALID,
            /** A valid deployment is selected. */
            VALID;
    }


    class DeploymentWorker extends SwingWorkerWithContext<List<Deployment>, Void> {

        @Override
        protected List<Deployment> doInBackgroundWithContext() throws Exception {
            var callWorkflowConnection =
                ConnectionUtil.createConnection(m_configuration).orElseThrow(this::cannotCreateConnection);
            if (callWorkflowConnection instanceof DeploymentExecutionConnector deploymentConnection) {
                return deploymentConnection.getServiceDeployments();
            }
            throw new InvalidSettingsException(
                "No Hub Authentication connection, please use the Hub Authenticator to list the available deployments");
        }

        InvalidSettingsException cannotCreateConnection() {
            return new InvalidSettingsException(
                String.format("Can not create the deployment execution connection for the deployment '%s'",
                    m_configuration.getDeploymentId()));
        }

        @Override
        protected void doneWithContext() {
            if (!isCancelled()) {
                try {
                    Optional.ofNullable(get())//
                        .ifPresent(DeploymentSelectionPanel.this::setDeployments);
                } catch (InterruptedException | CancellationException e) {
                    NodeLogger.getLogger(getClass()).warn(e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    setErrorMessage(ExceptionUtils.getRootCauseMessage(e));
                }
            }
        }
    }
    // state

    private Status m_state;

    /** The selected deployment id. The validity of the held data is indicated by m_state. */
    private Optional<String> m_selectedDeploymentId = Optional.empty();

    /**
     * As long as this is null, we're showing a loading message. Once this is set with {@link #setDeployments(List)},
     * the selected deployment is validated against the available deployments.
     */
    private List<Deployment> m_deployments;

    private Optional<Deployment> m_selectedDeployment = Optional.empty();

    private CallWorkflowConnectionConfiguration m_configuration;

    // controls

    private final JPanel m_panel = new JPanel();

    DeploymentSelectionDialogManager m_dialogManager;

    private final JLabel m_deploymentLabel = new JLabel("Deployment");

    private final JTextField m_deploymentTextField = new JTextField();

    private final JButton m_browseButton = new JButton("Browse...");

    private final JLabel m_statusLabel = new JLabel();

    private final FlowVariableModel m_deploymentFlowVariableModel;

    // async

    private DeploymentWorker m_deploymentWorker;

    DeploymentSelectionPanel(final CallWorkflowConnectionConfiguration configuration, final FlowVariableModel deploymentFlowVariableModel) {
        m_configuration = configuration;

        m_deploymentFlowVariableModel = deploymentFlowVariableModel;
        m_deploymentFlowVariableModel.addChangeListener(this::deploymentFlowVariableModelChanged);

        arrangeControls();

        m_deploymentTextField.setEnabled(false);
        m_browseButton.addActionListener(e -> m_dialogManager.open());

        m_dialogManager = new DeploymentSelectionDialogManager(m_panel);
        m_dialogManager.addDeploymentChangedListener(this::deploymentChangedListener);
        m_dialogManager.addRefreshListener(this::fetchDeployments);
    }

    // ------------------- External operations -------------------

    /**
     * @return the panel containing all controls and the deployment table
     */
    JPanel getPanel() {
        return m_panel;
    }

    /**
     * When the selected row in the deployments table changes, notify all listeners.
     *
     * @param listener notified every time the selected deployment is changed.
     */
    void addDeploymentChangedListener(final Consumer<Deployment> listener) {
        m_dialogManager.addDeploymentChangedListener(e -> {
            Deployment newDeployement = (Deployment)e.getNewValue();
            listener.accept(newDeployement);
        });
    }

    /**
     * @param id the id of the deployment to select or null if nothing should be selected.
     */
    void setSelectedDeploymentById(final String id) {
        m_selectedDeploymentId = Optional.ofNullable(id);

        // clears the text field if null
        m_deploymentTextField.setText(resolve(id, m_deployments).map(Deployment::name).orElse(id));

        validateSelectedDeploymentId();

        m_dialogManager.setSelectedDeploymentById(id);
    }

    /**
     * Updates the currently selected deployment name by resolving the selected deployment's id against the given list.
     *
     * @param deployments the deployments available for selection
     */
    void setDeployments(final List<Deployment> deployments) {
        m_deployments = deployments;
        m_dialogManager.setDeployments(deployments);

        String id = m_selectedDeploymentId.orElse(null);
        m_deploymentTextField.setText(resolve(id, m_deployments).map(Deployment::name).orElse(id));

        validateSelectedDeploymentId();
    }

    /**
     * @return the selected deployment or an empty optional if no or an invalid deployment is selected.
     */
    Optional<Deployment> getSelectedDeployment() {
        if (m_state != Status.VALID) {
            return Optional.empty();
        }
        return m_selectedDeployment;
    }

    /**
     * @param message error message, e.g., in case the deployments cannot be fetched
     */
    void setErrorMessage(final String message) {
        setState(Status.INVALID);
        m_statusLabel.setText("""
                <html>
                <p style="color:red">%s</p>
                </html>
                """.formatted(message));
    }

    /**
     * Loads the settings from the provided configuration and fetches available execution contexts.
     *
     * @param configuration the call workflow connection configuration.
     * @param settings
     */
    final void loadSettingsInDialog(final CallWorkflowConnectionConfiguration configuration,
        final NodeSettingsRO settings) {
        if(m_state != Status.CONTROLLED_BY_FLOW_VARIABLE) {
            setState(Status.VALIDATION_PENDING);
            setSelectedDeploymentById(configuration.getDeploymentId());
        }
        fetchDeployments();
    }

    /**
     * Stores the deployment id in the configuration.
     *
     * @param configuration the configuration
     */
    void saveToConfiguration(final CallWorkflowConnectionConfiguration configuration) {
        getSelectedDeployment()
            .ifPresent(deployment -> configuration.setDeploymentId(deployment.id()));
    }

    /**
     * Cancels the Execution context worker.
     */
    void close() {
        if (m_deploymentWorker != null) {
            m_deploymentWorker.cancel(true);
            m_deploymentWorker = null;
        }
    }

    // ------------------- Internal operations -------------------

    private void fetchDeployments() {
        //close the worker
        close();
        m_deploymentWorker = new DeploymentWorker();
        m_deploymentWorker.execute();
    }

    /** if the deployment id is set via a flow variable, disable the browse button */
    private void deploymentFlowVariableModelChanged(final ChangeEvent evt) {
        if(evt.getSource() instanceof FlowVariableModel fvm && fvm.isVariableReplacementEnabled()) {
            setState(Status.CONTROLLED_BY_FLOW_VARIABLE);
            fvm.getVariableValue().map(FlowVariable::getStringValue).ifPresent(this::setSelectedDeploymentById);
        } else {
            setState(Status.VALIDATION_PENDING);
        }
    }

    /**
     * Check that {@link #m_selectedDeploymentId} appears in {@link #m_deployments} and set an error otherwise.
     */
    private void validateSelectedDeploymentId() {
        setLoadingMessage("Validating selected deployment...");

        if(m_deployments == null) {
            setLoadingMessage("Loading deployments...");
            return;
        }

        if(m_selectedDeploymentId.isEmpty()) {
            // user needs to select a deployment
            setErrorMessage("No deployment selected.");
            return;
        }

        final var selectedId = m_selectedDeploymentId.get();
        var optionalMatch = resolve(selectedId, m_deployments);
        if (optionalMatch.isEmpty()) {
            setErrorMessage("The selected deployment does not exist.");
        } else {
            setState(Status.VALID);
        }
    }

    private void setState(final Status state) {
        m_state = state;
        m_statusLabel.setVisible(List.of(Status.VALIDATION_PENDING, Status.INVALID).contains(state));
        // a different status might be set while the deployment id is still being controlled by a flow variable
        // however, the flow variable model is initialized only after the node dialog is loaded and cannot be always trusted
        m_browseButton.setEnabled(state != Status.CONTROLLED_BY_FLOW_VARIABLE && !m_deploymentFlowVariableModel.isVariableReplacementEnabled());
    }

    /**
     * Called whenever the dialog has a new deployment id set. This might be on load, when restoring state or when the user interacts.
     * @param e contains the new and old deployment id.
     */
    private void deploymentChangedListener(final PropertyChangeEvent e) {
        var optDeployment = Optional.ofNullable((Deployment) e.getNewValue());

        if(optDeployment.isEmpty()) {
            m_selectedDeployment = Optional.empty();
            setState(Status.VALIDATION_PENDING);
        } else {
            m_selectedDeployment = optDeployment;
            var deployment = optDeployment.get();
            m_deploymentTextField.setText(deployment.name());
            m_deploymentTextField.setToolTipText(deploymentToolTip(deployment));
            setState(Status.VALID);
        }

    }


    private void setLoadingMessage(final String message) {
        setState(Status.VALIDATION_PENDING);
        m_statusLabel.setText(message);
    }

    private static String deploymentToolTip(final Deployment deployment) {
        return """
                <html>
                <p>Workflow Path: %s</p>
                <p>Execution Context: %s</p>
                <p>Deployment ID: %s</p>
                </html>
                """.formatted(deployment.workflowPath(), deployment.executionContextName(), deployment.id());
    }

    /**
     * @param deploymentId id to look up
     * @param deployments in this list
     * @return the deployment, if present and no parameter was null
     */
    static Optional<Deployment> resolve(final String deploymentId, final List<Deployment> deployments) {
        if (deploymentId == null || deployments == null) {
            return Optional.empty();
        }
        return deployments.stream()//
            .filter(d -> d.id().equals(deploymentId))//
            .findAny();
    }

    // ------------------- Constructor helpers -------------------

    /** layout the controls */
    private final void arrangeControls() {
        m_panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        c.insets = new Insets(5, 5, 5, 5);
        m_panel.add(m_deploymentLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        m_panel.add(m_deploymentTextField, c);
        c.gridx = 2;
        c.weightx = 0;
        m_panel.add(m_browseButton, c);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 3;
        c.weighty = 0;
        m_panel.add(m_statusLabel, c);
    }

}
