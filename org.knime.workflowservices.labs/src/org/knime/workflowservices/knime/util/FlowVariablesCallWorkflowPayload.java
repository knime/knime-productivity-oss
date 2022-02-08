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
 *   Created on Nov 13, 2021 by wiswedel
 */
package org.knime.workflowservices.knime.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.node.workflow.CredentialsStore;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.ICredentials;
import org.knime.core.node.workflow.IllegalFlowVariableNameException;
import org.knime.core.node.workflow.VariableType;
import org.knime.core.node.workflow.virtual.AbstractPortObjectRepositoryNodeModel;
import org.knime.core.util.FileUtil;

/**
 *
 * @author wiswedel
 */
final class FlowVariablesCallWorkflowPayload implements CallWorkflowPayload {

    private static final String CFG_PLAIN_VARIABLES = "variables";
    private static final String CFG_PASSWORDS = "passwords";

    /** Passwords are stored in temp files before they are sent to the receiving side. Since these files live on disc
     * we do the best to obfuscate the content by (weakly) encrypting the password using this key. */
    private static final String WEAK_ENCRYPT_PASS = "1u4#/5c2";

    private final List<FlowVariable> m_flowVariables;

    private FlowVariablesCallWorkflowPayload(final List<FlowVariable> flowVariables) {
        m_flowVariables = flowVariables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo, final AbstractPortObjectRepositoryNodeModel portObjRepoNodeModel) throws Exception {
        for (FlowVariable variable : m_flowVariables) {
            // this should be typed to the type of the variable value
            pushTo.accept(variable);
        }
        return FlowVariablePortObject.INSTANCE;
    }

    @Override
    public PortObjectSpec getSpec() {
        return FlowVariablePortObjectSpec.INSTANCE;
    }

    @Override
    public void close() throws IOException {
        // nothing to do here
    }

    static final FlowVariablesCallWorkflowPayload createFrom(final InputStream stream)
        throws IOException, InvalidSettingsException {
        List<FlowVariable> flowVariables = new ArrayList<>();
        var variablesParentSettings = NodeSettings.loadFromXML(stream);
        var variablesSettings = variablesParentSettings.getNodeSettings(CFG_PLAIN_VARIABLES);
        var passwordSettings = variablesParentSettings.getNodeSettings(CFG_PASSWORDS);
        for (String key : variablesSettings.keySet()) {
            var flowVar = FlowVariable.load(variablesSettings.getNodeSettings(key));
            if (flowVar.getVariableType().equals(VariableType.CredentialsType.INSTANCE)) {
                ICredentials credVar = flowVar.getValue(VariableType.CredentialsType.INSTANCE);
                var password = passwordSettings.getPassword(flowVar.getName(), WEAK_ENCRYPT_PASS);
                flowVar = CredentialsStore.newCredentialsFlowVariable(credVar.getName(), credVar.getLogin(), password,
                    false, false);
            }
            flowVariables.add(flowVar);
        }

        // when retrieving flow variables via NodeModel#getAvailableFlowVariables it returns top of stack first (even
        // though it is a map). Reverse to insert top of stack last.
        Collections.reverse(flowVariables);
        return new FlowVariablesCallWorkflowPayload(Collections.unmodifiableList(flowVariables));
    }

    /**
     * For instance the flow variable with the name "knime.workspace" is a reserved variable and can not be loaded using
     * {@link FlowVariable#load(NodeSettingsRO)} (which won't accept flow variables with reserved names).
     *
     * @param variable the flow variable to test for inclusion
     * @return whether the flow variable can be re-instantiated on the receiving side (the callee for a Workflow Service
     *         Input node, the caller for a Workflow Service Output node).
     */
    private static boolean isSendableFlowVariable(final FlowVariable variable) {
        try {
            FlowVariable.Scope.Flow.verifyName(variable.getName());
            return true;
        } catch (IllegalFlowVariableNameException e) { // NOSONAR
            return false;
        }
    }

    /**
     * Implementation of {@link CallWorkflowUtil#writeFlowVariables(Collection)}.
     */
    static File writeFlowVariables(final Collection<FlowVariable> flowVariables) throws IOException {
        List<FlowVariable> list = flowVariables.stream()//
            .filter(FlowVariablesCallWorkflowPayload::isSendableFlowVariable)//
            .collect(Collectors.toList());

        // flow variable port objects don't contain information, they just serve as a means to connect nodes
        // take the flow variables from the workflow manager's stack and write them to XML via NodeSettings
        var variables = new NodeSettings("flow-variables");
        var variablesSettings = variables.addNodeSettings(FlowVariablesCallWorkflowPayload.CFG_PLAIN_VARIABLES);
        var passwordSettings = variables.addNodeSettings(FlowVariablesCallWorkflowPayload.CFG_PASSWORDS);
        for (var i = 0; i < list.size(); i++) {
            var flowVariable = list.get(i);

            if (flowVariable.getVariableType().equals(VariableType.CredentialsType.INSTANCE)
                && Boolean.getBoolean(KNIMEConstants.PROPERTY_WEAK_PASSWORDS_IN_SETTINGS_FORBIDDEN)) {
                continue;
            }

            String key = "Var_" + i;
            flowVariable.save(variablesSettings.addNodeSettings(key));

            if (flowVariable.getVariableType().equals(VariableType.CredentialsType.INSTANCE)) {
                ICredentials c = flowVariable.getValue(VariableType.CredentialsType.INSTANCE);
                passwordSettings.addPassword(flowVariable.getName(), FlowVariablesCallWorkflowPayload.WEAK_ENCRYPT_PASS,
                    c.getPassword());
            }
        }
        var tempFile = FileUtil.createTempFile("external-node-flow-variables-", ".xml", false);
        try (var out = new FileOutputStream(tempFile)) {
            variables.saveToXML(out);
        }
        return tempFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<PortObject> getPortObject() {
        return Optional.of(FlowVariablePortObject.INSTANCE);
    }

}
