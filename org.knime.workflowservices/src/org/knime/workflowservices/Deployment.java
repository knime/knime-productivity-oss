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
 *   Created on 21 Feb 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.util.Objects;

/**
 * For exchanging data between backend and call workflow node dialogs (deployment selector).
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 *
 * @param name simple name that labels the deployment
 * @param id identifies the deployment
 * @param workflowPath the path of the workflow, e.g., <code>/Users/Development Team/Moritz Space/callees/TablePassThrough</code>
 * @param teamName the team that owns the deployment
 * @param executionContextName each deployment is bound to exactly one execution context
 * @param lastModified last update to this deployment
 */
public record Deployment(String name, String id, String workflowPath, String teamName, String executionContextName, String lastModified)  {

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Deployment other = (Deployment)obj;
        return Objects.equals(id, other.id);
    }
}
