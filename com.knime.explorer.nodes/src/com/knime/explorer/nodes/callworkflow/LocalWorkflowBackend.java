/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 17, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.json.JsonObject;

import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContainerState;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.workbench.ui.navigator.ProjectWorkflowMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * A local workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class LocalWorkflowBackend implements IWorkflowBackend, AutoCloseable {

    private static final LoadingCache<URI, LocalWorkflowBackend> CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(1L, TimeUnit.MINUTES)
            .maximumSize(2)
            .removalListener(
                new RemovalListener<URI, LocalWorkflowBackend>() {
                    @Override
                    public void onRemoval(final RemovalNotification<URI, LocalWorkflowBackend> notification) {
                        ProjectWorkflowMap.unregisterClientFrom(notification.getKey(), notification.getValue());
                        ProjectWorkflowMap.remove(notification.getKey());
                    }
                })
            .build(new CacheLoader<URI, LocalWorkflowBackend>() {
                @Override
                public LocalWorkflowBackend load(final URI uri) throws Exception {
                    File file = new File(uri);
                    WorkflowManager m = (WorkflowManager)ProjectWorkflowMap.getWorkflow(uri);
                    if (m == null) {
                        WorkflowLoadResult l = WorkflowManager.loadProject(
                            file, new ExecutionMonitor(), new WorkflowLoadHelper(file));
                        m = l.getWorkflowManager();
                        ProjectWorkflowMap.putWorkflow(uri, m);
                    }
                    final LocalWorkflowBackend localWorkflowBackend = new LocalWorkflowBackend(uri, m);
                    ProjectWorkflowMap.registerClientTo(uri, localWorkflowBackend);
                    return localWorkflowBackend;
                }
            });

    static LocalWorkflowBackend get(final String path) throws Exception {
        CACHE.cleanUp();
        URI uri = new URI(path);
        File file = ResolverUtil.resolveURItoLocalFile(uri);
        if (file == null || !file.isDirectory()) {
            throw new IOException("No such directory: " + uri);
        }
        file = file.getCanonicalFile();
        final LocalWorkflowBackend localWorkflowBackend = CACHE.get(file.toURI());
        localWorkflowBackend.setInUse();
        return localWorkflowBackend;
    }

    private final URI m_uri;
    private final WorkflowManager m_manager;
    private boolean m_isInUse;

    private LocalWorkflowBackend(final URI uri, final WorkflowManager m) {
        m_uri = uri;
        m_manager = m;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonObject> getInputNodes() {
        return m_manager.getInputNodes();
    }

    /** {@inheritDoc} */
    @Override
    public void setInputNodes(final Map<String, JsonObject> input) {
        m_manager.setInputNodes(input);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonObject> getOutputNodes() {
        return m_manager.getOutputNodes();
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowState execute() {
        m_manager.executeAllAndWaitUntilDone();
        NodeContainerState state = m_manager.getNodeContainerState();
        if (state.isExecuted()) {
            return WorkflowState.EXECUTED;
        } else if (state.isExecutionInProgress()) {
            return WorkflowState.RUNNING;
        } else {
            return WorkflowState.IDLE;
        }
    }

    void setInUse() throws IllegalStateException {
        CheckUtils.checkState(!m_isInUse, "Workflow instance %s already in use", m_manager.getNameWithID());
        m_isInUse = true;
    }

    /** {@inheritDoc} */
    @Override
    public void discard() {
        m_isInUse = false;
        KNIMETimer.getInstance().schedule(new TimerTask() {
            @Override
            public void run() {
                CACHE.cleanUp();
            }
        }, TimeUnit.SECONDS.toMillis(65L));
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws Exception {
        discard();
    }

}
