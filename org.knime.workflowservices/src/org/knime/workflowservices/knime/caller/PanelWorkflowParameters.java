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
 *   Created on 28 Sep 2021 by carlwitt
 */
package org.knime.workflowservices.knime.caller;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.lang3.NotImplementedException;
import org.knime.base.node.viz.plotter.Axis;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.ViewUtils;
import org.knime.filehandling.core.defaultnodesettings.status.DefaultStatusMessage;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage.MessageType;
import org.knime.filehandling.core.defaultnodesettings.status.StatusView;
import org.knime.workflowservices.Fetcher;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;
import org.knime.workflowservices.connection.util.LoadingPanel;

/**
 * Takes the input and output parameters of a workflow (parameter name, type) and displays them. Allows reordering of
 * the input and output parameters, which will be reflected on the order of the ports of the Call Workflow Service node.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 * @noreference non-public API
 */
public final class PanelWorkflowParameters implements Fetcher.Processor<WorkflowParameters, WorkflowParameters> {

    private static final DefaultStatusMessage PARAMETERS_OUT_OF_SYNC = new DefaultStatusMessage(MessageType.WARNING,
        "The node ports do not match the parameters of the workflow. Adjust?");

    private static final DefaultStatusMessage PARAMETERS_IN_SYNC =
        new DefaultStatusMessage(MessageType.INFO, "The node ports match the parameters of the workflow.");

    /** What this panel currently shows */
    public enum State {
            /** Initial state. Nothing to display, no workflow selected. */
            NO_WORKFLOW_SELECTED,
            /**
             * When retrieving the workflow input/output parameters from a workflow. This will hide the current
             * parameter mapping and show a progress indicator.
             */
            LOADING,
            /**
             * When the callee workflow defines neither input nor output parameters, display a special message instead
             * of empty parameter lists.
             */
            /** An error occured during fetching the workflow parameters. Hide all controls. */
            ERROR,
            /**
             * Workflow parameters do not match the Call Workflow Service's ports. Shows a button to confirm that the
             * node's ports should be adjusted. The button will simply switch to state {@link #READY} (
             */
            PARAMETER_CONFLICT,
            /** Workflow has parameters, they are fetched and displayed and allow reordering. */
            READY;
    }

    /** a function to check whether the workflow parameters match the node's ports. {@link #checkParametersInSync()} */
    private final Predicate<WorkflowParameters> m_workflowParametersInSync;

    private final CardLayout m_cardLayout = new CardLayout();

    /** Root panel shows either */
    private final JPanel m_panel = new JPanel(m_cardLayout);

    /** For showing and reordering the port assignments of the workflow input parameters. */
    private final PanelOrderableParameters m_inputMapping;

    /** For showing and reordering the port assignments of the workflow output parameters. */
    private final PanelOrderableParameters m_outputMapping;

    private final JLabel m_errorLabel = new JLabel();

    private final LoadingPanel m_fetchingParametersPanel = new LoadingPanel("Fetching workflow parameters...");

    /** Warns the user when the node ports do not match the workflow parameter types (or their order). */
    private final StatusView m_outOfSyncWarning = new StatusView(500);

    /** Used to confirm that node ports should be updated. */
    private final JButton m_confirmNodePortAdjustment = new JButton("Adjust node ports");

    /**
     * The ports configuration of the Call Workflow Service node. Used to find a valid default ordering of workflow
     * parameters.
     */
    private final PortsConfiguration m_nodePortsConfiguration;

    /**
     * The deprecated Call Workflow Service node always has a file system input port - we want to ignore it in
     * #update(WorkflowParameters). The Call Workflow Service node with the dynamic file system input port may or may
     * not have the port. If a connector port is present, its offset is stored here, otherwise null;
     */
    private final CallWorkflowConnectionConfiguration m_connectionConfiguration;

    private State m_currentState;

    /**
     * The properties set during the latest call to {@link #update(WorkflowParameters)}. If update is called with
     * compatible properties, no reorder will take place.
     */
    private WorkflowParameters m_currentProperties = null;

    /**
     * As soon as the user interacts with the reorder buttons (see {@link #checkParametersInSync()}, the selected order
     * will be accepted as user defined workflow parameter order.
     */
    private boolean m_userDefinedOrder = false;

    /**
     * @param workflowParametersInSync a function to check whether the workflow parameters match the node's ports
     * @param configuration provides the offset of the file system connector/callee location port if one is present
     * @param nodePortsConfiguration
     */
    public PanelWorkflowParameters(final Predicate<WorkflowParameters> workflowParametersInSync,
        final CallWorkflowConnectionConfiguration configuration, final PortsConfiguration nodePortsConfiguration) {

        m_workflowParametersInSync = workflowParametersInSync;
        m_nodePortsConfiguration = nodePortsConfiguration;
        m_connectionConfiguration = configuration;

        m_inputMapping =
            new PanelOrderableParameters("Input Parameters", //
            (parameter, portIndex) -> String.format("Input Port %s (%s)", portIndex + 1,
                parameter.getPortType().getName()), //
            (parameter, portIndex) -> parameter.getParameterName(), //
            this::reorderCallback);
        m_outputMapping = new PanelOrderableParameters("Output Parameters", //
            (parameter, portIndex) -> parameter.getParameterName(), //
            (parameter, portIndex) -> String.format("Output Port %s (%s)", portIndex + 1,
                parameter.getPortType().getName()), //
            this::reorderCallback);

        m_confirmNodePortAdjustment.addActionListener(l -> setState(State.READY));

        m_panel.add(createNoWorkflowSelectedPanel(), State.NO_WORKFLOW_SELECTED.name());
        m_panel.add(m_fetchingParametersPanel.getPanel(), State.LOADING.name());
        m_panel.add(createErrorPanel(), State.ERROR.name());
        m_panel.add(createReadyPanel(), State.READY.name());

        setState(State.NO_WORKFLOW_SELECTED);
    }

    private static JPanel createNoWorkflowSelectedPanel() {
        var noWorkflowSelectedPanel = new JPanel(new GridBagLayout());
        noWorkflowSelectedPanel
            .add(new JLabel("Please select a workflow by entering a path or clicking \"Browse...\"."));
        return noWorkflowSelectedPanel;
    }

    private JPanel createErrorPanel() {
        var errorPanel = new JPanel(new GridBagLayout());
        m_errorLabel.setForeground(Color.RED.darker());
        errorPanel.add(m_errorLabel);
        return errorPanel;
    }

    /** Shows the workflow's input and output parameters and their ordering. */
    private JComponent createReadyPanel() {

        var syncCheckPanel = new Box(Axis.HORIZONTAL);
        syncCheckPanel.add(m_outOfSyncWarning.getPanel());
        syncCheckPanel.add(m_confirmNodePortAdjustment);

        var parametersPanel = new Box(Axis.VERTICAL);
        parametersPanel.add(m_inputMapping.getGUIPanel());
        parametersPanel.add(m_outputMapping.getGUIPanel());
        parametersPanel.add(syncCheckPanel);

        return parametersPanel;
    }

    /**
     * @return the panel that contains all components of this GUI element.
     */
    public JPanel getContentPane() {
        return m_panel;
    }

    /**
     * Called during loadSettings of the Call Worflow Serivce node dialog. Since the user-defined order is stored in the
     * node settings, it must not be changed when initializing the panel.
     */
    public void load(final WorkflowParameters workflowParameters) {
        m_userDefinedOrder = true;
        m_currentProperties = workflowParameters.copy();
        accept(workflowParameters);
    }

    @Override
    public void accept(final WorkflowParameters p) {
        set(p);
    }

    /**
     * Rebuild the panel contents to display the mapping between Call Workflow Service node ports and callee workflow
     * parameters.
     *
     * If the given parameters match the previous parameters in name and type, the update will have no effect, in order
     * to preserve user-defined orderings of the ports/parameters.
     *
     * Called asynchronously after successfully fetching parameters for a workflow.
     *
     * @param workflowParameters new workflow input/output parameters
     */
    @Override
    public void set(final WorkflowParameters workflowParameters) {

        // if a user-defined order exists, try to apply it
        if (m_userDefinedOrder) {
            // m_userDefinedOrder == true implies m_currentProperties != null
            if (!m_currentProperties.compatible(workflowParameters)) { // NOSONAR
                // the user defined order is not applicable and thus invalid
                m_userDefinedOrder = false;
            }
            // otherwise just keep the current properties (which reflect the user defined order)
        }

        // if no user-defined order exists, try to reorder the parameters according to the node ports.
        // NB: None (parameters differ from ports) or several (multiple ports of the same type) such orders may exist.
        if (!m_userDefinedOrder) {
            m_currentProperties = workflowParameters.copy();

            // the data ports, i.e., excluding the file system connector
            List<PortType> inputPorts = new LinkedList<>(Arrays.asList(m_nodePortsConfiguration.getInputPorts()));
            // remove the i-th port that corresponds to the file system connector
            m_connectionConfiguration.getConnectorPortIndex().ifPresent(i -> inputPorts.remove(i.intValue()));
            m_currentProperties.sort(inputPorts.toArray(PortType[]::new), m_nodePortsConfiguration.getOutputPorts());
        }

        m_inputMapping.update(m_currentProperties.getInputParameters());
        m_outputMapping.update(m_currentProperties.getOutputParameters());

        setState(State.READY);

        checkParametersInSync();
        ViewUtils.runOrInvokeLaterInEDT(() -> {
            m_panel.repaint();
            m_panel.revalidate();
        });
    }

    /** Called whenever the user interacts with the buttons to order the workflow parameters. */
    void reorderCallback() {
        m_userDefinedOrder = true;
        checkParametersInSync();
    }

    /**
     * Compare the workflow parameters to the node ports, show a warning if they do not match.
     */
    void checkParametersInSync() {

        try {
            m_currentProperties.setInputParameterOrder(getInputParameterOrder());
            m_currentProperties.setOutputParameterOrder(getOutputParameterOrder());
            if (!m_workflowParametersInSync.test(m_currentProperties)) {
                setState(State.PARAMETER_CONFLICT);
            } else {
                setState(State.READY);
            }
        } catch (InvalidSettingsException e) {
            // should never happen
            throw new IllegalStateException("Coding error. Cannot reorder workflow parameters.", e);
        }

    }

    /**
     * @return the names of the parameters in the order they are currently in the dialog
     */
    public String[] getInputParameterOrder() {
        return m_inputMapping.getParameterOrder();
    }

    /**
     * @return the names of the parameters in the order they are currently in the dialog
     */
    public String[] getOutputParameterOrder() {
        return m_outputMapping.getParameterOrder();
    }

    @Override
    public void exception(final String message) {
        m_errorLabel.setText("Error: " + message);
        setState(State.ERROR);
    }

    /**
     * @param state adjust display according to a given state.
     */
    public void setState(final State state) {
        // display the READY panel both for state READY and PARAMETER_CONFLICT
        String panel = state == State.PARAMETER_CONFLICT ? State.READY.name() : state.name();

        m_currentState = state;
        ViewUtils.runOrInvokeLaterInEDT(() -> {
            // enable sync button only when in conflict
            m_confirmNodePortAdjustment.setEnabled(state == State.PARAMETER_CONFLICT);
            // show warning when parameters are out of sync
            m_outOfSyncWarning
                .setStatus(state == State.PARAMETER_CONFLICT ? PARAMETERS_OUT_OF_SYNC : PARAMETERS_IN_SYNC);

            m_cardLayout.show(getContentPane(), panel);
            getContentPane().repaint();
            getContentPane().revalidate();
        });

    }

    public State getState() {
        return m_currentState;
    }

    @Override
    public WorkflowParameters get() {
        return m_currentProperties;
    }

    @Override
    public void addListener(final PropertyChangeListener listener) {
        throw new NotImplementedException("The workflow parameters panel does not allow listener registration");
    }

    @Override
    public void clear() {
        m_currentProperties = null;
        this.setState(State.NO_WORKFLOW_SELECTED);
    }

    @Override
    public void loading() {
        this.setState(State.LOADING);
    }


}