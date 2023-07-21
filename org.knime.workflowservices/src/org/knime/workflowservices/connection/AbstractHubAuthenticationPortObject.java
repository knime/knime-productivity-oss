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
 *   Created on 8 Feb 2023 by Dionysios Stolis
 */
package org.knime.workflowservices.connection;

import org.knime.core.node.port.PortObject;

/**
 * Abstract port object to decouple the dependecy to the Hub Port object (closed source code).
 *
 * @author Dionysios Stolis, KNIME GmbH, Berlin, Germany
 */
public interface AbstractHubAuthenticationPortObject extends PortObject {

    /**
     * Serializer registered via extension point but expected not to be called (since class is abstract).
     *
     * @noreference Not to be used.
     */
    final class FailSerializer extends FailOnInvocationPortObjectSerializer<AbstractHubAuthenticationPortObject> {
    }

    @Override
    AbstractHubAuthenticationPortObjectSpec getSpec(); // constrain return type

}
