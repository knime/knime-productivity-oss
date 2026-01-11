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

import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.widget.choices.Label;

enum JsonInputOption {
        @Label(value = "Default",
            description = "send nothing, causing the default JSON object defined by the according "
                + "Container Input node to be used. The called workflow is executed once, "
                + "the result is reused for all subsequent rows. Note that if the called workflow "
                + "has been saved with one of the output nodes in executed state, the return value "
                + "for that output value is the json value \"null\".")
        DEFAULT, //
        @Label(value = "Custom JSON", description = "Provide a custom JSON value for the parameter.")
        CUSTOM, //

        @Label(value = "From Column",
            description = "Select column of the input table. The called workflow is executed for each row.")
        COLUMN;

    interface Ref extends ParameterReference<JsonInputOption> {
    }

    static final class IsCustom implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(Ref.class).isOneOf(CUSTOM);
        }

    }

    static final class IsColumn implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(Ref.class).isOneOf(COLUMN);
        }

    }

}