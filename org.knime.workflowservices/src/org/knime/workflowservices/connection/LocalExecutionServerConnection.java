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
 *   Created on Nov 14, 2020 by wiswedel
 */
package org.knime.workflowservices.connection;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.workflowservices.IWorkflowBackend;
import org.knime.workflowservices.json.row.caller.local.LocalWorkflowBackend;

/**
 * Not a server connection (despite the name) but local workflow execution.
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 * @noreference This method is not intended to be referenced by clients.
 */
public final class LocalExecutionServerConnection implements IServerConnection {

    private WorkflowManager m_wfm;

    public LocalExecutionServerConnection(final WorkflowManager wfm) {
        m_wfm = wfm;
    }

    @Override
    public IWorkflowBackend createWorkflowBackend(final CallWorkflowConnectionConfiguration configuration)
        throws Exception {
        return LocalWorkflowBackend.newInstance(configuration.getWorkflowPath(), m_wfm);
    }

    @Override
    public List<String> listWorkflows() throws ListWorkflowFailedException {
        final Path root = m_wfm.getContext().getMountpointRoot().toPath();

        final List<String> workflows = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                    throws IOException {
                    if (dir.getFileName().toString().equals(".metadata")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path workflowFile = dir.resolve(WorkflowPersistor.WORKFLOW_FILE);
                    Path templateFile = dir.resolve(WorkflowPersistor.TEMPLATE_FILE);

                    if (Files.exists(workflowFile)) {
                        if (!Files.exists(templateFile)) {
                            String workflowPath = "/" + root.relativize(dir).toString();
                            if (Platform.getOS().equals(Platform.OS_WIN32)) {
                                workflowPath = workflowPath.replace('\\', '/');
                            }
                            workflows.add(workflowPath);
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    } else {
                        return super.preVisitDirectory(dir, attrs);
                    }
                }
            });
        } catch (IOException e) {
            throw new ListWorkflowFailedException(e);
        }

        Collections.sort(workflows, String.CASE_INSENSITIVE_ORDER);
        return workflows;
    }

    @Override
    public void close() throws IOException {
        LocalWorkflowBackend.cleanCalledWorkflows(m_wfm);
    }

    @Override
    public String getHost() {
        return "No server connection";
    }

}
