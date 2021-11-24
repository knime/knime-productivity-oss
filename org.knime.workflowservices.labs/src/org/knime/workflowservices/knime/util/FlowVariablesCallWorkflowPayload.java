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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettings;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.node.workflow.CredentialsStore;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.ICredentials;
import org.knime.core.node.workflow.VariableType;

/**
 *
 * @author wiswedel
 */
final class FlowVariablesCallWorkflowPayload implements CallWorkflowPayload {

    static final String CFG_PLAIN_VARIABLES = "variables";
    static final String CFG_PASSWORDS = "passwords";

    static final String WEAK_ENCRYPT_PASS = "1u4#/5c2";

    private final List<FlowVariable> m_flowVariables;

    private FlowVariablesCallWorkflowPayload(final List<FlowVariable> flowVariables) {
        m_flowVariables = flowVariables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PortObject onExecute(final ExecutionContext exec, final Consumer<FlowVariable> pushTo) throws Exception {
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

}
