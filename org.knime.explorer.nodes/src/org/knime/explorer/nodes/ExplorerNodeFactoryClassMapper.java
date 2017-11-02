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
 *   Created on Jun 20, 2017 by wiswedel
 */
package org.knime.explorer.nodes;

import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeFactoryClassMapper;
import org.knime.core.node.NodeModel;
import org.knime.explorer.nodes.browser.ExplorerBrowserNodeFactory;
import org.knime.explorer.nodes.callworkflow.local.CallLocalWorkflowNodeFactory;
import org.knime.explorer.nodes.writer.ExplorerWriterNodeFactory;

/**
 * Maps old class names "com.knime.explorer.nodes.*" to new open source class names (new package suffix included).
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class ExplorerNodeFactoryClassMapper extends NodeFactoryClassMapper {

    @Override
    public NodeFactory<? extends NodeModel> mapFactoryClassName(final String factoryClassName) {
        switch (factoryClassName) {
            case "com.knime.explorer.nodes.ExplorerBrowserNodeFactory":
                return new ExplorerBrowserNodeFactory();
            case "com.knime.explorer.nodes.ExplorerWriterNodeFactory":
                return new ExplorerWriterNodeFactory();
            case "com.knime.explorer.nodes.callworkflow.local.CallLocalWorkflowNodeFactory":
                return new CallLocalWorkflowNodeFactory();
            default:
                return null;
        }
    }

}
