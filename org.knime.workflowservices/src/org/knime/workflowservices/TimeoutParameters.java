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
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;
import org.knime.workflowservices.CallWorkflowLayout.JobStatusPollingSection;
import org.knime.workflowservices.CallWorkflowLayout.TimeoutsSection;

/**
 * Timeout parameters for Call Workflow nodes.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 */
@LoadDefaultsForAbsentFields
final class TimeoutParameters implements NodeParameters {

    @Widget(title = "Workflow load timeout",
        description = "The maximum amount of time to wait for the remote executor when trying to initialize"
            + " remote workflow execution. Specified in seconds. A value of 0 means that no timeout will be used.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class, stepSize = 30)
    @Layout(TimeoutsSection.class)
    int m_loadTimeout = 60;

    @Widget(title = "Fetch parameters timeout",
        description = "The maximum amount of time to wait for the remote executor when fetching the input and "
            + "output parameters of the called workflow. "
            + "Specified in seconds. A value of 0 means that no timeout will be used.")
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class, stepSize = 30)
    @Layout(TimeoutsSection.class)
    int m_fetchParametersTimeout = 10;

    @Layout(JobStatusPollingSection.class)
    BackoffPolicyParameters m_backoffPolicy = new BackoffPolicyParameters();

    static class BackoffPolicyParameters implements NodeParameters {

        @Widget(title = "Base timeout (milliseconds)",
            description = "During asynchronous invocation (option \"Long duration\"), "
                + "if a HTTP 5XX error occurs when polling for the job status, "
                + "the node will retry the request. The node will wait a certain amount of time before each retry. "
                + "The time to wait before the <i>n</i>-th attempt is determined by <i>base * multiplier^n</i>.")
        @NumberInputWidget(minValidation = IsNonNegativeValidation.class, stepSize = 50)
        @Persist(configKey = "backoffBase")
        long m_base = 1200;

        @Widget(title = "Multiplier",
            description = "The multiplier for the backoff delay calculation. "
                + "Use a multiplier of 1 for a constant backoff, or a multiplier greater than 1 for "
                + "an exponential backoff.")
        @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
        @Persist(configKey = "backoffMultiplier")
        long m_multiplier = 1;

        @Widget(title = "Maximum number of retries",
            description = "The maximum number of retries. Set to 0 to disable retrying.")
        @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
        @Persist(configKey = "backoffRetries")
        int m_retries = 3;
    }

}
