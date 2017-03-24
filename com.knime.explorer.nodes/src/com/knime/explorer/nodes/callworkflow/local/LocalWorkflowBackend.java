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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.json.JsonValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.birt.report.engine.api.EngineException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.workflow.NodeContainerState;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeMessage;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.util.FileUtil;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.Pair;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.ExplorerURLStreamHandler;
import org.knime.workbench.explorer.filesystem.LocalExplorerFileStore;
import org.knime.workbench.ui.navigator.ProjectWorkflowMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.knime.enterprise.reportexecutor.ReportExecutor;
import com.knime.enterprise.utility.oda.ReportingConstants;
import com.knime.enterprise.utility.oda.ReportingConstants.RptOutputFormat;
import com.knime.productivity.base.callworkflow.IWorkflowBackend;

/**
 * A local workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 *
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class LocalWorkflowBackend implements IWorkflowBackend {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(LocalWorkflowBackend.class);

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

                if (Boolean.getBoolean("java.awt.headless")) {
                    WorkflowContext.Factory ctxFac;
                    if (NodeContext.getContext() != null) {
                         ctxFac =
                            new WorkflowContext.Factory(NodeContext.getContext().getWorkflowManager().getContext());
                         ctxFac.setCurrentLocation(file);
                    } else {
                        ctxFac = new WorkflowContext.Factory(file);
                    }
                    WorkflowLoadResult l = WorkflowManager.loadProject(file, new ExecutionMonitor(),
                        new WorkflowLoadHelper(ctxFac.createContext()));
                    return new LocalWorkflowBackend(uri, l.getWorkflowManager());
                } else {
                    WorkflowManager m = (WorkflowManager)ProjectWorkflowMap.getWorkflow(uri);
                    if (m == null) {
                        WorkflowContext.Factory ctxFac = new WorkflowContext.Factory(file);

                        LocalExplorerFileStore fs = ExplorerMountTable.getFileSystem().fromLocalFile(file);
                        if (fs != null) {
                            File mountpointRoot = fs.getContentProvider().getFileStore("/").toLocalFile();
                            ctxFac.setMountpointRoot(mountpointRoot);
                            ctxFac.setMountpointURI(fs.toURI());
                        } else if (NodeContext.getContext() != null) {
                            // use context from current workflow if available
                            WorkflowContext tmp = NodeContext.getContext().getWorkflowManager().getContext();
                            ctxFac.setMountpointRoot(tmp.getMountpointRoot());
                        }

                        WorkflowLoadResult l = WorkflowManager.loadProject(file, new ExecutionMonitor(),
                            new WorkflowLoadHelper(ctxFac.createContext()));
                        m = l.getWorkflowManager();
                        ProjectWorkflowMap.putWorkflow(uri, m);
                    }
                    LocalWorkflowBackend localWorkflowBackend = new LocalWorkflowBackend(uri, m);
                    ProjectWorkflowMap.registerClientTo(uri, localWorkflowBackend);
                    return localWorkflowBackend;
                }
            }
        });

    private static final Map<WorkflowManager, Set<URI>> CALLER_MAP = new WeakHashMap<>();

    static LocalWorkflowBackend newInstance(final String path, final WorkflowManager callingWorkflow) throws Exception {
        CACHE.cleanUp();

        Path workflowDir;
        URL resolvedUrl;
        try {
            resolvedUrl = ExplorerURLStreamHandler.resolveKNIMEURL(new URL(path));
        } catch (IOException ex) {
            // no URI, try mountpoint relative path instead
            if (path.startsWith("/")) { // absolute path
                workflowDir = callingWorkflow.getContext().getMountpointRoot().toPath().resolve(path).toRealPath();
            } else {
                workflowDir = callingWorkflow.getContext().getCurrentLocation().toPath().resolve(path).toRealPath();
            }
            resolvedUrl = workflowDir.toUri().toURL();
        }

        if (resolvedUrl.getProtocol().equalsIgnoreCase("file")) {
            workflowDir = FileUtil.resolveToPath(resolvedUrl);
        } else {
            assert resolvedUrl.getProtocol().startsWith("http") : "Expected http URL but not " + resolvedUrl;
            workflowDir = downloadAndExtractRemoteWorkflow(new URL(path));
        }

        if (!Files.isDirectory(workflowDir)) {
            throw new IOException("No such workflow: " + workflowDir);
        }

        URI workflowUri = workflowDir.toUri();
        final LocalWorkflowBackend localWorkflowBackend = CACHE.get(workflowUri);
        localWorkflowBackend.lock();

        if (!resolvedUrl.getProtocol().equalsIgnoreCase("file")) {
            // workflow downloaded from server
            localWorkflowBackend.m_deleteAfterUse = true;
        }

        synchronized (CALLER_MAP) {
            CALLER_MAP.computeIfAbsent(callingWorkflow, k -> new HashSet<>()).add(workflowUri);
        }

        return localWorkflowBackend;
    }

    private static Path downloadAndExtractRemoteWorkflow(final URL url) throws IOException {
        File tempDir = FileUtil.createTempDir("Called-workflow");
        File zippedWorkflow = new File(tempDir, "workflow.knwf");

        try (OutputStream os = new FileOutputStream(zippedWorkflow); InputStream is = url.openStream()) {
            IOUtils.copy(is, os);
        } catch (IOException ex) {
            if (ex.getMessage().contains("Server returned HTTP response code: 403")) {
                throw new IOException("User does not have permissions to read workflow " + url + " on the server", ex);
            } else {
                throw ex;
            }
        }

        FileUtil.unzip(zippedWorkflow, tempDir);
        Files.delete(zippedWorkflow.toPath());
        return tempDir.listFiles()[0].toPath();
    }

    static void cleanCalledWorkflows(final WorkflowManager callingWorkflow) {
        synchronized (CALLER_MAP) {
            Set<URI> workflowsUsedBy = CALLER_MAP.remove(callingWorkflow);
            if (workflowsUsedBy != null) {
                for (URI workflowUri : workflowsUsedBy) {
                    CACHE.invalidate(workflowUri);
                }
                CACHE.cleanUp();
            }
        }
    }

    private final URI m_uri;

    private final WorkflowManager m_manager;

    private final ReentrantLock m_inUse = new ReentrantLock();

    private boolean m_discardAfterUse;

    // set when the workflow has been downloaded from the server into a temporary directory
    private boolean m_deleteAfterUse;

    private LocalWorkflowBackend(final URI uri, final WorkflowManager m) {
        m_uri = uri;
        m_manager = m;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, ExternalNodeData> getInputNodes() {
        return m_manager.getInputNodes();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonValue> getOutputValues() {
        Map<String, JsonValue> map = new HashMap<>();

        for (Map.Entry<String, ExternalNodeData> e : m_manager.getExternalOutputs().entrySet()) {
            JsonValue json = e.getValue().getJSONValue();
            if (json != null) {
                map.put(e.getKey(), json);
            }
        }

        return map;
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowState execute(final Map<String, ExternalNodeData> input) throws Exception {
        setInputNodes(input);
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
        if (!Boolean.getBoolean("java.awt.headless")) {
            ProjectWorkflowMap.unregisterClientFrom(m_uri, this);
            ProjectWorkflowMap.remove(m_uri);
        }
        if (m_deleteAfterUse) {
            FileUtil.deleteRecursively(new File(m_uri));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputNodes(final Map<String, ExternalNodeData> input) throws InvalidSettingsException {
        m_manager.setInputNodes(input);
    }

    /** {@inheritDoc}
     * @throws ReportGenerationException */
    @Override
    public byte[] generateReport(final RptOutputFormat format) throws ReportGenerationException {
        File reportDocDir = null;
        try {
            LOGGER.debug("Starting report document generation for workflow \"" + m_manager.getName() + "\".");
            reportDocDir = ReportExecutor.runReport(m_manager);
        } catch (EngineException e) {
            throw new ReportGenerationException("The generation of the report document failed.", e);
        } catch (IOException e) {
            throw new ReportGenerationException("Reading the report design file or writing the report document failed: "
                    + e.getMessage(), e);
        }
        try {
            byte[] report = ReportExecutor.renderReport(m_manager, reportDocDir, format,
                new ReportingConstants.RptOutputOptions());
            LOGGER.debugWithFormat("Successfully rendered report (%s) for workflow '%s'; report is %s large",
                format.getExtension(), m_manager.getName(), FileUtils.byteCountToDisplaySize(report.length));
            return report;
        } catch (EngineException e) {
            throw new ReportGenerationException(
                "The report document does not exist, is invalid, or could not be rendered.", e);
        } finally {
            FileUtils.deleteQuietly(reportDocDir);
        }
    }
}
