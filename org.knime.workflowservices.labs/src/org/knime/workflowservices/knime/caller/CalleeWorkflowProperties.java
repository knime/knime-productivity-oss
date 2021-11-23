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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.JsonValue;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.dialog.InputNode;
import org.knime.core.node.dialog.OutputNode;
import org.knime.core.node.port.PortType;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.productivity.base.callworkflow.IWorkflowBackend.ResourceContentType;
import org.knime.workflowservices.knime.CalleeWorkflowData;

/**
 * Input out output parameter groups of an external KNIME workflow to be called by {@link CallWorkflowNodeModel}.
 *
 * @author Carl Witt, KNIME GmbH, Berlin, Germany
 */
class CalleeWorkflowProperties {

    private static final String CFG_OUTPUTS = "outputs";
    private static final String CFG_INPUTS = "inputs";

    /**
     * Contains one entry for each Subworkflow Input node in the subworkflow. The first element of the pair is the
     * node's ID, the second is the description of the parameter(s) it declares.
     */
    private final List<CalleeWorkflowData> m_inputNodes;

    /**
     * See {@link #m_inputNodes}
     */
    private final List<CalleeWorkflowData> m_outputNodes;

    /**
     * Unwraps and filters the input and output nodes for those that specify subworkflow inputs/outputs. (The maps
     * initially contain the external node data objects provided by ANY node in the subworkflow that implements
     * {@link InputNode} or {@link OutputNode}.
     *
     * @param inputNodes map from input id {@link ExternalNodeData#getID()} to the external node data's JSON payload
     *            {@link ExternalNodeData#getJSONValue()}.
     * @param outputNodes like above, just for the output nodes
     */
    public CalleeWorkflowProperties(final Map<String, ResourceContentType> inputNodes,
        final Map<String, ResourceContentType> outputNodes) {
        this.m_inputNodes = unwrapAndFilter(inputNodes);
        this.m_outputNodes = unwrapAndFilter(outputNodes);
    }

    /**
     * @param inputNodes each entry consists of the node id of the Subworkflow Input node and the parameters it
     *            declares. The order will be reflected for instance in the {@link CallWorkflowNodeDialog} and also in
     *            the order of the dynamic ports of the Subworkflow node.
     * @param outputNodes like above just for the Subworkflow Output nodes in the subworkflow.
     */
    public CalleeWorkflowProperties(final List<CalleeWorkflowData> inputNodes,
        final List<CalleeWorkflowData> outputNodes) {
        m_inputNodes = inputNodes;
        m_outputNodes = outputNodes;
    }

    /**
     * Unwraps the parameter descriptions from a {@link JsonValue} to a {@link CalleeWorkflowData}.
     *
     * Filters the entries that can not be converted to {@link CalleeWorkflowData} (e.g., other nodes in the
     * workflow implementing {@link InputNode}).
     *
     * Sorts the ports by the parameter name that is assigned to them to get a deterministic order.
     *
     * @param map retrieved from the {@link IWorkflowBackend}
     */
    private static List<CalleeWorkflowData> unwrapAndFilter(final Map<String, ResourceContentType> parameters) {
        Comparator<CalleeWorkflowData> sortOrder = Comparator//
            .comparing(CalleeWorkflowData::getPortType, Comparator.comparing(PortType::getName))//
            .thenComparing(CalleeWorkflowData::getParameterName);
        return parameters.entrySet().stream()//
            // keep key (node id), unwrap input/output descriptions
            .map(e -> {
                CalleeWorkflowData desc = null;
                try {
                    // doesn't validate the parameter name, because it might be changed by the framework if not unique
                    // in the callee (e.g., from input-parameter to input-parameter-<node id> which isn't a valid
                    // parameter name for a user to choose
                    desc = new CalleeWorkflowData(e.getKey(), e.getValue().toPortType());
                } catch (InvalidSettingsException ise) {
                    // TODO better error handling (in dialog) e.g. missing port type in submit client
                    NodeLogger.getLogger(CalleeWorkflowData.class).error(ise.getMessage(), ise);
                }
                return desc;
            })//
            .filter(c -> c != null) //
            // sort by node id to get deterministic order
            .sorted(sortOrder).collect(Collectors.toList());
    }

    public boolean hasInputParameter(final PortType ofType) {
        return m_inputNodes.stream().map(CalleeWorkflowData::getPortType).anyMatch(ofType::equals);
    }

    /**
     * @return the inputNodes
     */
    public List<CalleeWorkflowData> getInputNodes() {
        return m_inputNodes;
    }

    /**
     * @return the outputNodes
     */
    public List<CalleeWorkflowData> getOutputNodes() {
        return m_outputNodes;
    }

    /**
     * Saves object to argument, used during 'save' in model or dialog.
     *
     * @param settings To save to.
     */
    public void saveTo(final NodeSettingsWO settings) {
        var inputSettings = settings.addNodeSettings(CFG_INPUTS);
        for (var i = 0 ; i < m_inputNodes.size(); i++) {
            var t = inputSettings.addNodeSettings("input-" + i);
            m_inputNodes.get(i).saveTo(t);
        }
        var outputSettings = settings.addNodeSettings(CFG_OUTPUTS);
        for (var i = 0 ; i < m_outputNodes.size(); i++) {
            var t = outputSettings.addNodeSettings("output-" + i);
            m_outputNodes.get(i).saveTo(t);
        }
    }

    /**
     * Loads new object from settings, previously saved with {@link #saveTo(NodeSettingsWO)}.
     *
     * @param settings to load from
     * @return a new object loaded from the settings
     * @throws InvalidSettingsException If that fails
     */
    public static CalleeWorkflowProperties loadFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        var inputSettings = settings.getNodeSettings(CFG_INPUTS);
        var inputNodes = new ArrayList<CalleeWorkflowData>();
        for (var key : inputSettings.keySet()) {
            var t = inputSettings.getNodeSettings(key);
            var c = CalleeWorkflowData.loadFrom(t);
            inputNodes.add(c);
        }
        var outputSettings = settings.getNodeSettings(CFG_OUTPUTS);
        var outputNodes = new ArrayList<CalleeWorkflowData>();
        for (var key : outputSettings.keySet()) {
            var t = outputSettings.getNodeSettings(key);
            var c = CalleeWorkflowData.loadFrom(t);
            outputNodes.add(c);
        }
        return new CalleeWorkflowProperties(inputNodes, outputNodes);
    }

}