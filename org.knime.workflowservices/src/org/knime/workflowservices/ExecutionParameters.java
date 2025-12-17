/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 */

package org.knime.workflowservices;

import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.migration.DefaultProvider;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.migration.Migration;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.persistence.legacy.EnumBooleanPersistor;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.workflowservices.CallWorkflowLayout.ExecutionSettingsSection;

/**
 * Execution parameters for Call Workflow nodes.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 */
@LoadDefaultsForAbsentFields
@Layout(ExecutionSettingsSection.class)
class ExecutionParameters implements NodeParameters {

    enum InvocationDuration {
            @Label(value = "Long duration",
                description = "Choose this if the called workflow is expected to run for more than "
                    + "ten seconds. When selected, the invocation will poll its status repeatedly until the "
                    + "job completes (or fails). The polling is implemented so that for the first few seconds "
                    + "there will be frequent status checks (poll interval 100ms or 500ms), whereas jobs that "
                    + "run minutes or hours will be checked only every few seconds.")
            LONG_DURATION,
            @Label(value = "Short duration",
                description = "Choose this if the called workflow is expected to finish within ten seconds. "
                    + "When selected, the invocation will not poll its status but wait until the job "
                    + "completes (or fails). This removes the polling overhead and makes this option quicker "
                    + "for short-running workflows. Choosing this option for long-running workflows "
                    + "(> a minute) will cause timeout problems.")
            SHORT_DURATION;
    }

    @Widget(title = "Invocation duration",
        description = "Choose the expected duration of the called workflow execution.")
    @ValueSwitchWidget
    @Persistor(InvocationDurationPersistor.class)
    @Migration(InvocationDurationDefaultProvider.class)
    InvocationDuration m_invocationDuration;

    @SuppressWarnings("restriction")
    static final class InvocationDurationPersistor extends EnumBooleanPersistor<InvocationDuration> {

        InvocationDurationPersistor() {
            super("isSynchronous", InvocationDuration.class, InvocationDuration.SHORT_DURATION);
        }
    }

    /**
     * Default provider for InvocationDuration. The old default was synchronous = false, which corresponds to
     * LONG_DURATION (asynchronous).
     */
    static final class InvocationDurationDefaultProvider implements DefaultProvider<InvocationDuration> {

        @Override
        public InvocationDuration getDefault() {
            return InvocationDuration.LONG_DURATION;
        }
    }

    @Widget(title = "Retain job on failure",
        description = "When selected, failing jobs of the called workflow will be kept on the remote executor. "
            + "This can be useful for debugging purposes. When not selected, the failing jobs will be "
            + "discarded.")
    boolean m_keepFailingJobs = true;

    @Widget(title = "Discard job on successful execution",
        description = "When selected, successful jobs of the called workflow will be discarded from the "
            + "remote executor. When not selected, successful jobs will be kept.")
    boolean m_discardJobOnSuccessfulExecution = true;

}
