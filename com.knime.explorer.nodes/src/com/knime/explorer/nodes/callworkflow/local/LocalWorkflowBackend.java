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
package com.knime.explorer.nodes.callworkflow.local;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.json.JsonObject;

import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.workflow.NodeContainerState;
import org.knime.core.node.workflow.NodeMessage;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.Pair;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.workbench.ui.navigator.ProjectWorkflowMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.knime.explorer.nodes.callworkflow.IWorkflowBackend;

/**
 * A local workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class LocalWorkflowBackend implements IWorkflowBackend {
    private static final LoadingCache<URI, LocalWorkflowBackend> CACHE = CacheBuilder.newBuilder()
        .expireAfterAccess(1L, TimeUnit.MINUTES).maximumSize(5)
        .removalListener(new RemovalListener<URI, LocalWorkflowBackend>() {
            @Override
            public void onRemoval(final RemovalNotification<URI, LocalWorkflowBackend> notification) {
                LocalWorkflowBackend value = notification.getValue();
                if (value.isInUse()) {
                    value.setDiscardAfterUse();
                } else {
                    value.discard();
                }
            }
        }).build(new CacheLoader<URI, LocalWorkflowBackend>() {
            @Override
            public LocalWorkflowBackend load(final URI uri) throws Exception {
                File file = new File(uri);
                WorkflowManager m = (WorkflowManager)ProjectWorkflowMap.getWorkflow(uri);
                if (m == null) {
                    WorkflowLoadResult l =
                        WorkflowManager.loadProject(file, new ExecutionMonitor(), new WorkflowLoadHelper(file));
                    m = l.getWorkflowManager();
                    ProjectWorkflowMap.putWorkflow(uri, m);
                }
                final LocalWorkflowBackend localWorkflowBackend = new LocalWorkflowBackend(uri, m);
                ProjectWorkflowMap.registerClientTo(uri, localWorkflowBackend);
                return localWorkflowBackend;
            }
        });

    static LocalWorkflowBackend newInstance(final String path, final WorkflowContext wfContext) throws Exception {
        CACHE.cleanUp();
        File workflowDir;
        try {
            URI uri = new URI(path);
            workflowDir = ResolverUtil.resolveURItoLocalFile(uri).getCanonicalFile();
        } catch (URISyntaxException | IOException ex) {
            // no URI, try mountpoint relative path instead
            if (path.startsWith("/")) { // absolute path
                workflowDir = new File(wfContext.getMountpointRoot(), path).getCanonicalFile();
            } else {
                workflowDir = new File(wfContext.getCurrentLocation(), path).getCanonicalFile();
            }

        }
        if (!workflowDir.isDirectory()) {
            throw new IOException("No such workflow: " + workflowDir);
        }

        final LocalWorkflowBackend localWorkflowBackend = CACHE.get(workflowDir.toURI());
        localWorkflowBackend.lock();
        return localWorkflowBackend;
    }

    private final URI m_uri;

    private final WorkflowManager m_manager;

    private final ReentrantLock m_inUse = new ReentrantLock();

    private boolean m_discardAfterUse;

    private LocalWorkflowBackend(final URI uri, final WorkflowManager m) {
        m_uri = uri;
        m_manager = m;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, ExternalNodeData> getInputNodes() {
        return m_manager.getInputNodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputNodes(final Map<String, ExternalNodeData> input) throws InvalidSettingsException {
        m_manager.setInputNodes(input);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonObject> getOutputValues() {
        Map<String, JsonObject> map = new HashMap<>();

        for (Map.Entry<String, ExternalNodeData> e : m_manager.getExternalOutputs().entrySet()) {
            JsonObject json = e.getValue().getJSONObject();
            if (json != null) {
                map.put(e.getKey(), json);
            }
        }

        return map;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getWorkflowMessage() {
        List<Pair<String, NodeMessage>> errors = m_manager.getNodeMessages(NodeMessage.Type.ERROR);
        if (!errors.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Pair<String, NodeMessage> p : errors) {
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(p.getFirst()).append(": ").append(p.getSecond().getMessage());
            }
            return b.toString();
        }
        List<Pair<String, NodeMessage>> warnings = m_manager.getNodeMessages(NodeMessage.Type.WARNING);
        if (!warnings.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Pair<String, NodeMessage> p : warnings) {
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(p.getFirst()).append(": ").append(p.getSecond().getMessage());
            }
            return b.toString();
        }
        return m_manager.getParent().printNodeSummary(m_manager.getID(), 0);
    }

    void lock() throws InterruptedException {
        m_inUse.lockInterruptibly();
    }

    boolean isInUse() {
        return m_inUse.isLocked();
    }

    void setDiscardAfterUse() {
        m_discardAfterUse = true;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws Exception {
        try {
            m_manager.getParent().cancelExecution(m_manager);
            if (m_discardAfterUse) {
                discard();
            }
            KNIMETimer.getInstance().schedule(new TimerTask() {
                @Override
                public void run() {
                    CACHE.cleanUp();
                }
            }, TimeUnit.SECONDS.toMillis(65L));
        } finally {
            m_inUse.unlock();
        }
    }

    void discard() {
        ProjectWorkflowMap.unregisterClientFrom(m_uri, this);
        ProjectWorkflowMap.remove(m_uri);
    }
}
