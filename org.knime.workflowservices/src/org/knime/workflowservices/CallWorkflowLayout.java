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
 *   Created on Dec 15, 2025 by paulbaernreuther
 */
package org.knime.workflowservices;

import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.workflowservices.CommonParameters.IsRemoteExecution;

/**
 * Layout interface for the Call Workflow node dialog.
 * @since 5.10
 */
@SuppressWarnings("javadoc")
public interface CallWorkflowLayout {

    @Section(title = "Execution Settings")
    @Effect(predicate = IsRemoteExecution.class, type = EffectType.SHOW)
    interface ExecutionSettingsSection {
    }

    @After(ExecutionSettingsSection.class)
    @Section(title = "Workflow")
    interface WorkflowOrDeploymentSection {
    }

    @Advanced
    @After(WorkflowOrDeploymentSection.class)
    @Section(title = "Connection Timeouts")
    @Effect(predicate = IsRemoteExecution.class, type = EffectType.SHOW)
    interface TimeoutsSection {
    }

    @Section(title = "Job Status Polling",
        description = "During asynchronous invocation, if a HTTP 5XX error occurs when polling for the job status, "
            + "the node will retry the request. The node will wait a certain amount of time before each retry. "
            + "The time to wait before the <i>n</i>-th attempt is determined by <i>base * multiplier^n</i>.")
    @After(TimeoutsSection.class)
    @Effect(predicate = IsRemoteExecution.class, type = EffectType.SHOW)
    @Advanced
    interface JobStatusPollingSection {
    }

}
