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
 *   Created on Jan 12, 2026 by paulbaernreuther
 */
package org.knime.workflowservices.json.table.caller2;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.knime.core.data.json.container.credentials.ContainerCredentialsJsonSchema;
import org.knime.core.data.json.container.table.ContainerTableJsonSchema;
import org.knime.node.parameters.NodeParameters;
import org.knime.workflowservices.IWorkflowBackend;

import jakarta.json.JsonValue;

final class ParameterIdsUtil {

    private ParameterIdsUtil() {
        // utility class
    }

    static final class ParameterId implements NodeParameters {
        String m_fullyQualifiedId;

        String m_simpleId;

        String getId(final boolean useFullyQualifiedName) {
            return useFullyQualifiedName ? m_fullyQualifiedId : m_simpleId;
        }
    }

    static ParameterId[] getParameterIdsArray(final Collection<String> nodeValuesKeys) {
        return IWorkflowBackend.getFullyQualifiedToSimpleIDMap(nodeValuesKeys).entrySet().stream().map(e -> {
            ParameterId pid = new ParameterId();
            pid.m_fullyQualifiedId = e.getKey();
            pid.m_simpleId = e.getValue();
            return pid;
        }).toArray(ParameterId[]::new);
    }

    record InputParameterKeys(Collection<String> tableInputKeys, Collection<String> flowVariableInputKeys,
        Collection<String> credentialVariableInputKeys) {

    }

    /**
     * Given a map of input node data, each entry corresponding to a container input node in the called workflow, we
     * want to partition these entries by type of container input node s.t. we can constrain the range of choices to
     * only those of matching type (e.g. as a target container input node for table data the user should only be able to
     * select Container Input (Table) nodes). There is currently no clean way to determine the type of Container Input
     * node based on the given input node data. As a heuristic, this is determined based on the example/template JSON
     * that some container input nodes provide. Table and Credential inputs provide characteristic template JSON. Flow
     * Variable input nodes, however, can accept any JSON containing key/value pairs (since AP-16680). Partition the
     * given map based on matching the provided template JSONs against each node's schema. Provide table and credential
     * inputs with only those choices that can be matched, all other cases are eligible to be pushed as flow variables.
     * See AP-17403.
     *
     *
     * @param inputValues from the workflow backend
     * @return the input parameter keys categorized by type
     */
    static InputParameterKeys getInputParameterKeys(final Map<String, JsonValue> inputValues) {
        Set<String> tableInputs = new HashSet<>();
        Set<String> credentialInputs = new HashSet<>();
        Set<String> otherInputs = new HashSet<>();
        for (Entry<String, JsonValue> entry : inputValues.entrySet()) {
            if (ContainerTableJsonSchema.hasContainerTableJsonSchema(entry.getValue())) {
                tableInputs.add(entry.getKey());
            } else if (ContainerCredentialsJsonSchema.hasValidSchema(entry.getValue())) {
                credentialInputs.add(entry.getKey());
            } else {
                otherInputs.add(entry.getKey());
            }
        }
        return new InputParameterKeys(tableInputs, otherInputs, credentialInputs);
    }
}
