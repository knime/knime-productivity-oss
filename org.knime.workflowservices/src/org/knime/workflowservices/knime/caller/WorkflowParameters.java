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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.IWorkflowBackend.ResourceContentType;

/**
 * Input and output parameters of a KNIME workflow.
 *
 * Imposes an ordering on the otherwise unordered parameters. This will be reflected in in the Call Workflow Node Dialog
 * and the Call Workflow node's port order.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
public class WorkflowParameters {

    private static final String CFG_OUTPUTS = "outputs";

    private static final String CFG_INPUTS = "inputs";

    /**
     * Default sort order for input and output parameters.
     */
    private static final Comparator<WorkflowParameter> DEFAULT_PARAMETER_SORT_ORDER = Comparator//
        .comparing(WorkflowParameter::getPortType, Comparator.comparing(PortType::getName))//
        .thenComparing(WorkflowParameter::getParameterName);

    /**
     * Contains one entry for each Workflow Service Input node in the callee workflow.
     */
    private List<WorkflowParameter> m_inputParameters;

    /**
     * See {@link #m_inputParameters}
     */
    private List<WorkflowParameter> m_outputParameters;

    /**
     * Orders the input and output parameters by sorting them according to default order.
     *
     * @param inputParameters as returned by {@link IWorkflowBackend#getInputResourceDescription()}
     * @param outputParameters as returned by {@link IWorkflowBackend#getOutputResourceDescription()}
     */
    public WorkflowParameters(final Map<String, ResourceContentType> inputParameters,
        final Map<String, ResourceContentType> outputParameters) {
        this.m_inputParameters = combineAndSort(inputParameters);
        this.m_outputParameters = combineAndSort(outputParameters);
    }

    /**
     * Constructor for the case that an ordering of the parameters already exists.
     *
     * @param inputParameters each entry consists of the node id of the workflow Input node and the parameters it
     *            declares. The order will be reflected in the Call Workflow Service node dialog and in the order of the
     *            dynamic ports of the Call Workflow Service node.
     * @param outputParameters like above just for the Workflow Service Output nodes in the workflow.
     */
    public WorkflowParameters(final List<WorkflowParameter> inputParameters,
        final List<WorkflowParameter> outputParameters) {
        m_inputParameters = inputParameters;
        m_outputParameters = outputParameters;
    }

    /**
     * Return a new, identical instance.
     */
    WorkflowParameters copy() {
        var settings = new NodeSettings("config");
        saveTo(settings);
        try {
            return WorkflowParameters.loadFrom(settings);
        } catch (InvalidSettingsException e) {
            throw new IllegalStateException("Coding error. Cannot copy workflow parameters.", e);
        }
    }

    /**
     * Combines parameter name and port type into a {@link WorkflowParameter} object.
     *
     * Sorts the parameters according to a default sorting order.
     *
     * @param map retrieved from the {@link IWorkflowBackend}
     */
    private static List<WorkflowParameter> combineAndSort(final Map<String, ResourceContentType> parameters) {
        return parameters.entrySet().stream()//
            // keep key (node id), unwrap input/output descriptions
            .map(e -> {
                WorkflowParameter desc = null;
                try {
                    // doesn't validate the parameter name, because it might be changed by the framework if not unique
                    // in the callee (e.g., from input-parameter to input-parameter-<node id> which isn't a valid
                    // parameter name for a user to choose
                    desc = new WorkflowParameter(e.getKey(), e.getValue().toPortType());
                } catch (InvalidSettingsException ise) {
                    // TODO better error handling (in dialog) e.g. missing port type in submit client
                    NodeLogger.getLogger(WorkflowParameter.class).error(ise.getMessage(), ise);
                }
                return desc;
            }).filter(Objects::nonNull) //
            .sorted(DEFAULT_PARAMETER_SORT_ORDER)//
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * @return the input parameters, in default order or in the order set with {@link #setInputParameterOrder(String[])}
     */
    @SuppressWarnings("javadoc")
    public List<WorkflowParameter> getInputParameters() {
        return m_inputParameters;
    }

    /**
     * @return the output parameters, in default order or in the order set with
     *         {@link #setOutputParameterOrder(String[])}
     */
    @SuppressWarnings("javadoc")
    public List<WorkflowParameter> getOutputParameters() {
        return m_outputParameters;
    }

    /**
     * Saves object to argument, used during 'save' in model or dialog.
     *
     * @param settings To save to.
     */
    public void saveTo(final NodeSettingsWO settings) {
        var inputSettings = settings.addNodeSettings(CFG_INPUTS);
        for (var i = 0 ; i < m_inputParameters.size(); i++) {
            var t = inputSettings.addNodeSettings("input-" + i);
            m_inputParameters.get(i).saveTo(t);
        }
        var outputSettings = settings.addNodeSettings(CFG_OUTPUTS);
        for (var i = 0 ; i < m_outputParameters.size(); i++) {
            var t = outputSettings.addNodeSettings("output-" + i);
            m_outputParameters.get(i).saveTo(t);
        }
    }

    /**
     * Loads new object from settings, previously saved with {@link #saveTo(NodeSettingsWO)}.
     *
     * @param settings to load from
     * @return a new object loaded from the settings
     * @throws InvalidSettingsException If that fails
     */
    @SuppressWarnings("javadoc")
    public static WorkflowParameters loadFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        var inputSettings = settings.getNodeSettings(CFG_INPUTS);
        var inputNodes = new ArrayList<WorkflowParameter>();
        for (var key : inputSettings.keySet()) {
            var t = inputSettings.getNodeSettings(key);
            var c = WorkflowParameter.loadFrom(t);
            inputNodes.add(c);
        }
        var outputSettings = settings.getNodeSettings(CFG_OUTPUTS);
        var outputNodes = new ArrayList<WorkflowParameter>();
        for (var key : outputSettings.keySet()) {
            var t = outputSettings.getNodeSettings(key);
            var c = WorkflowParameter.loadFrom(t);
            outputNodes.add(c);
        }
        return new WorkflowParameters(inputNodes, outputNodes);
    }

    /**
     * @param parameterNames the names of the input parameters in the order they should be represented in the Call
     *            Workflow Service node dialog and the Call Workflow Service node ports
     * @throws InvalidSettingsException if given non-existing, too many, or too few parameter names
     */
    public void setInputParameterOrder(final String[] parameterNames) throws InvalidSettingsException {
        m_inputParameters = reorder(m_inputParameters, parameterNames);
    }

    /**
     * @param parameterNames the names of the output parameters in the order they should be represented in the Call
     *            Workflow Service node dialog and the Call Workflow Service node ports
     * @throws InvalidSettingsException if given non-existing, too many, or too few parameter names
     */
    public void setOutputParameterOrder(final String[] parameterNames) throws InvalidSettingsException {
        m_outputParameters = reorder(m_outputParameters, parameterNames);
    }

    /**
     * @param newProperties parameters of a workflow
     * @return true iff other instance declares with parameters with identical names and types (irrespective of the
     *         ordering)
     */
    boolean compatible(final WorkflowParameters newProperties) {
        return parametersCompatible(getInputParameters(), newProperties.getInputParameters()) && //
            parametersCompatible(getOutputParameters(), newProperties.getOutputParameters());
    }

    /**
     * @return Whether both lists declare the same parameters (name and type)
     */
    private static boolean parametersCompatible(final List<WorkflowParameter> oldParameters,
        final List<WorkflowParameter> newParameters) {

        if (newParameters.size() != oldParameters.size()) {
            return false;
        }

        Map<String, PortType> newInputParameters = newParameters.stream()
            .collect(Collectors.toMap(WorkflowParameter::getParameterName, WorkflowParameter::getPortType));

        for (WorkflowParameter oldParameter : oldParameters) {
            var newPortType = newInputParameters.get(oldParameter.getParameterName());
            if (!Objects.equals(newPortType, oldParameter.getPortType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param parameters list to reorder, unchanged
     * @param names new order of the parameter names
     * @return a new list containing the parameter names and types in the order specified in names
     * @throws InvalidSettingsException if given non-existing, too many, or too few parameter names, or a port type does
     *             not exist
     */
    private static List<WorkflowParameter> reorder(final List<WorkflowParameter> parameters, final String[] names)
        throws InvalidSettingsException {

        CheckUtils.checkSetting(names.length == parameters.size(), "Cannot reorder input parameters: too %s parameters",
            names.length < parameters.size() ? "few" : "many");

        Map<String, PortType> parameterMap = parameters.stream()
            .collect(Collectors.toMap(WorkflowParameter::getParameterName, WorkflowParameter::getPortType));

        List<WorkflowParameter> reordered = new ArrayList<>();

        for (String name : names) {
            var portType = parameterMap.get(name);
            CheckUtils.checkNotNull(portType, "The parameter %s does not exist in the properties.", name);
            reordered.add(new WorkflowParameter(name, portType));
        }

        return reordered;
    }

    /**
     * Reorder the input and output parameters in a way that their types match the given port types. If this is not
     * possible (too few/many parameters, wrong types) the order may partially change.
     *
     * @param inputPorts the Call Workflow Service node's input port types
     * @param outputPorts the Call Workflow Service node's output port types
     * @return iff the reorder was successful
     */
    boolean sort(final PortType[] inputPorts, final PortType[] outputPorts) {
        boolean inputsSorted = sortParameters(m_inputParameters, inputPorts);
        boolean outputsSorted = sortParameters(m_outputParameters, outputPorts);
        return inputsSorted && outputsSorted;
    }

    /**
     * Move the parameters in the list such that they match the given port type order. If they already match, the order
     * won't be changed.
     *
     * @return true iff the parameters could be reordered such that parameters[i].type == portTypes[i] for all 0 <= i <
     *         portTypes.length
     */
    private static boolean sortParameters(final List<WorkflowParameter> parameters, final PortType[] portTypes) {
        // iterate over port types
        for (var portIdx = 0; portIdx < portTypes.length; portIdx++) {
            // for each port type, find a matching parameter and move it to the same offset in the list as
            // the port type's offset in the array
            var somethingFound = false;
            // skip the beginning of the list, these parameters are already taken
            for (var paramIdx = portIdx; paramIdx < parameters.size() && !somethingFound; paramIdx++) {
                if (parameters.get(paramIdx).getPortType().equals(portTypes[portIdx])) {
                    // take the parameter (at position paramIdx) and move it to the offset matching the port type
                    Collections.swap(parameters, paramIdx, portIdx);
                    somethingFound = true;
                }
            }
            // if we could not find a parameter matching the port type stop.
            if (!somethingFound) {
                return false;
            }
        }
        return true;
    }

}