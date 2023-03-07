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

/**
 * For exchanging data between backend and call workflow node dialogs (execution context selector).
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 *
 * @param name simple name that labels the execution context
 * @param id identifies the execution context
 * @param isDefault whether this is the default execution context for the currently connected space
 *
 * @noreference Non-public API. This type is not intended to be referenced by clients.
 */
public record ExecutionContext(String id, String name, boolean isDefault)  {

}
