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
 *   Created on Jan 9, 2026 by paulbaernreuther
 */
package org.knime.workflowservices.json.row.caller3;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.json.JSONValue;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ColumnChoicesProvider;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.choices.util.ColumnSelectionUtil;
import org.knime.node.parameters.widget.text.TextAreaWidget;
import org.knime.workflowservices.json.row.caller3.CalleeParameters.ContainerInputParametersRef;

/**
 * One of the input parameters presented as options in the Call Workflow (Row Based) node dialog.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 */
final class ContainerInputParameters implements NodeParameters {

    ContainerInputParameters() {
        // Default constructor for deserialization
    }

    ContainerInputParameters(final String key, final ExternalNodeData nodeData) {
        m_parameterName = key;
        m_inputOption = JsonInputOption.DEFAULT;

        // Initialize with default values
        var jsonValue = nodeData.getJSONValue();
        if (jsonValue != null) {
            m_customJson = jsonValue.toString();
        }
    }

    @Widget(title = "Parameter name",
        description = "Name of the input parameter in the called workflow. This parameter is read-only.")
    @Effect(predicate = AlwaysTrue.class, type = EffectType.DISABLE)
    String m_parameterName;

    static final class AlwaysTrue implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.always();
        }
    }

    @Widget(title = "Input mode", description = "Select how the input value should be provided.")
    @ValueReference(JsonInputOption.Ref.class)
    @ValueSwitchWidget
    JsonInputOption m_inputOption = JsonInputOption.DEFAULT;

    @Widget(title = "Custom JSON", description = "Enter the custom JSON value to be passed to the input node.")
    @Effect(predicate = JsonInputOption.IsCustom.class, type = Effect.EffectType.SHOW)
    @TextAreaWidget
    String m_customJson = "{}";

    @Widget(title = "JSON column",
        description = "Select the column containing JSON values to be passed to the input node.")
    @Effect(predicate = JsonInputOption.IsColumn.class, type = Effect.EffectType.SHOW)
    @ChoicesProvider(JsonColumnChoicesProvider.class)
    @ValueReference(JsonColumnRef.class)
    @ValueProvider(AutoGuessJsonColumnProvider.class)
    String m_jsonColumn;

    interface JsonColumnRef extends ParameterReference<String> {
    }

    static final class AutoGuessJsonColumnProvider implements StateProvider<String> {

        private Supplier<String> m_currentValue;

        private Supplier<JsonInputOption> m_inputOption;

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeOnValueChange(ContainerInputParametersRef.class);
            m_currentValue = initializer.computeFromValueSupplier(JsonColumnRef.class);
            m_inputOption = initializer.computeFromValueSupplier(JsonInputOption.Ref.class);

        }

        @SuppressWarnings("restriction") // StateComputationFailureException is not public
        @Override
        public String computeState(final NodeParametersInput parametersInput) throws StateComputationFailureException {
            if (m_inputOption.get() != JsonInputOption.COLUMN) {
                throw new StateComputationFailureException();
            }
            String currentValue = m_currentValue.get();
            if (currentValue != null && !currentValue.isEmpty()) {
                throw new StateComputationFailureException();
            }
            final var inTableSpec = getInPortSpec(parametersInput);
            if (inTableSpec.isEmpty()) {
                throw new StateComputationFailureException();
            }
            return ColumnSelectionUtil.getFirstCompatibleColumn(inTableSpec.get(), JSONValue.class)
                .orElseThrow(StateComputationFailureException::new).getName();
        }

    }

    static final class JsonColumnChoicesProvider implements ColumnChoicesProvider {

        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput parametersInput) {

            final var inTableSpec = getInPortSpec(parametersInput);
            if (inTableSpec.isEmpty()) {
                return List.of();
            }
            return ColumnSelectionUtil.getCompatibleColumns(inTableSpec.get(), JSONValue.class);
        }

    }

    private static final Optional<DataTableSpec> getInPortSpec(final NodeParametersInput input) {
        final var inputIndices = input.getPortsConfiguration().getInputPortLocation()
            .get(CallWorkflowRowBased3NodeFactory.INPUT_PORT_GRP_NAME);
        if (inputIndices.length > 0) {
            return input.getInTableSpec(inputIndices[0]);
        }
        return Optional.empty();

    }

}
