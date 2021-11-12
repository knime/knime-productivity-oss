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
 * ---------------------------------------------------------------------
 *
 * History
 *   Created on Feb 17, 2015 by wiswedel
 */
package org.knime.explorer.nodes.callworkflow.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.json.JsonValue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.core.runtime.CoreException;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.dialog.ExternalNodeData;
import org.knime.core.node.workflow.NodeContainerState;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeMessage;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowContext;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.util.FileUtil;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.LockFailedException;
import org.knime.core.util.Pair;
import org.knime.core.util.report.ReportingConstants;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.productivity.base.callworkflow.IWorkflowBackend;
import org.knime.reporting.executor.ReportExecutor;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.ExplorerURLStreamHandler;
import org.knime.workbench.explorer.filesystem.LocalExplorerFileStore;
import org.knime.workbench.ui.navigator.ProjectWorkflowMap;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * A local workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class LocalWorkflowBackend implements IWorkflowBackend {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(LocalWorkflowBackend.class);

    private static final Cache<URI, LocalWorkflowBackend> CACHE = CacheBuilder.newBuilder()
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
        }).build();

    private static final Map<WorkflowManager, Set<URI>> CALLER_MAP = new WeakHashMap<>();

    /**
     * Creates a new local workflow backend.
     *
     * @param path path to the workflow
     * @param callingWorkflow the calling workflow
     * @return a new local workflow backend
     * @throws Exception if the path does not point to a workflow
     */
    public static LocalWorkflowBackend newInstance(final String path, final WorkflowManager callingWorkflow) throws Exception {
        CACHE.cleanUp();

        URL originalUrl;
        try {
            originalUrl = new URL(path);
        } catch (MalformedURLException ex) {
            // no URL, try mountpoint relative path instead; for backwards-compatibility only, new nodes always
            // use a URL
            if (path.startsWith("/")) { // absolute path
                originalUrl = new URL("knime", ExplorerURLStreamHandler.MOUNTPOINT_RELATIVE, path);
            } else {
                originalUrl = new URL("knime", ExplorerURLStreamHandler.WORKFLOW_RELATIVE, "/" + path);
            }
        }

        // resolve relative URLs into absolute URLs, usually either file or http
        URL resolvedUrl = ExplorerURLStreamHandler.resolveKNIMEURL(originalUrl);

        Path workflowDir;
        if (resolvedUrl.getProtocol().equalsIgnoreCase("file")) {
            workflowDir = FileUtil.resolveToPath(resolvedUrl);
        } else {
            assert resolvedUrl.getProtocol().startsWith("http") : "Expected http URL but not " + resolvedUrl;
            workflowDir = downloadAndExtractRemoteWorkflow(originalUrl);
        }

        if (!Files.isDirectory(workflowDir)) {
            throw new IOException("No such workflow: " + workflowDir);
        }

        // Converting the path directly to URI leads to key matching problems in the ProjectWorkflowMap as three
        // slashes will be added after the scheme part of the URI, however the value will be accessed with only one slash
        // after the scheme. Hence, convert to file first. Furthermore, keys will be accessed with normalized URIs. See AP-7589.
        URI localUri = workflowDir.toFile().toURI().normalize();
        URL ou = originalUrl; // Just to make the compiler happy
        final LocalWorkflowBackend localWorkflowBackend = CACHE.get(localUri, () -> loadWorkflow(localUri, ou));
        localWorkflowBackend.lock();

        if (!resolvedUrl.getProtocol().equalsIgnoreCase("file")) {
            // workflow downloaded from server into temp directory, should be deleted after use
            localWorkflowBackend.m_deleteAfterUse = true;
        }

        synchronized (CALLER_MAP) {
            CALLER_MAP.computeIfAbsent(callingWorkflow, k -> new HashSet<>()).add(localUri);
        }

        return localWorkflowBackend;
    }

    /**
     * Loads a workflow.
     *
     * @param localUri the physical location in the local file system; may be a temporary copy of the workflow
     * @param originalUrl the original URL as configured by the user, e.g. knime://knime.workflow/../Called
     * @return a new local backend
     */
    private static LocalWorkflowBackend loadWorkflow(final URI localUri, final URL originalUrl)
        throws IOException, InvalidSettingsException, CanceledExecutionException, UnsupportedWorkflowVersionException,
        LockFailedException, CoreException {
        File file = new File(localUri);

        if (Boolean.getBoolean("java.awt.headless")) {
            // running as an executor on the server or as batch executor
            WorkflowContext.Factory ctxFac;

            if (NodeContext.getContext() != null) {
                WorkflowContext callerContext = NodeContext.getContext().getWorkflowManager().getContext();
                ctxFac = callerContext.createCopy();

                // compute the new path in the server repository base on the caller's path and the URL type
                if (callerContext.getRemoteRepositoryAddress().isPresent()
                    && callerContext.getRelativeRemotePath().isPresent()) {
                     String relPath;
                     if (ExplorerURLStreamHandler.WORKFLOW_RELATIVE.equalsIgnoreCase(originalUrl.getHost())) {
                        relPath = callerContext.getRelativeRemotePath().get() + "/" + originalUrl.getPath();
                     } else if (ExplorerURLStreamHandler.MOUNTPOINT_RELATIVE.equalsIgnoreCase(originalUrl.getHost())) {
                         relPath = originalUrl.getPath();
                     } else {
                         // this shouldn't happen
                         relPath = originalUrl.getPath();
                     }

                    ctxFac.setRemoteAddress(callerContext.getRemoteRepositoryAddress().get(),
                        FilenameUtils.normalize(relPath, true));
                 }
                 ctxFac.setCurrentLocation(file);
            } else {
                ctxFac = new WorkflowContext.Factory(file);
            }
            WorkflowLoadResult l = WorkflowManager.loadProject(file, new ExecutionMonitor(),
                new WorkflowLoadHelper(ctxFac.createContext()));
            return new LocalWorkflowBackend(localUri, l.getWorkflowManager());
        } else {
            // running in GUI mode
            WorkflowManager m = (WorkflowManager)ProjectWorkflowMap.getWorkflow(localUri);
            if (m == null) {
                WorkflowContext.Factory ctxFac = new WorkflowContext.Factory(file);

                LocalExplorerFileStore fs = ExplorerMountTable.getFileSystem().fromLocalFile(file);
                if (fs != null) {
                    File mountpointRoot = fs.getContentProvider().getRootStore().toLocalFile();
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
                ProjectWorkflowMap.putWorkflow(localUri, m);
            }
            LocalWorkflowBackend localWorkflowBackend = new LocalWorkflowBackend(localUri, m);
            ProjectWorkflowMap.registerClientTo(localUri, localWorkflowBackend);
            return localWorkflowBackend;
        }
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

    /**
     * Cleans all called workflows.
     *
     * @param callingWorkflow the workflow manager that has called the workflows
     */
    public static void cleanCalledWorkflows(final WorkflowManager callingWorkflow) {
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

    @Override
    public Map<String, ResourceContentType> getInputResourceDescription() {
        return filterResourceBasedExternalNodeData(m_manager.getInputNodes());
    }

    @Override
    public Map<String, ResourceContentType> getOutputResourceDescription() {
        return filterResourceBasedExternalNodeData(m_manager.getExternalOutputs());
    }

    private static Map<String, ResourceContentType>
        filterResourceBasedExternalNodeData(final Map<String, ExternalNodeData> values) {
        var result = new LinkedHashMap<String, ResourceContentType>();
        for (var entry : values.entrySet()) {
            var externalNodeData = entry.getValue();
            var contentType = externalNodeData.getContentType()//
                .map(ResourceContentType::of)//
                .filter(ResourceContentType::isKNIMEPortType);
            if (contentType.isPresent()) {
                result.put(entry.getKey(), contentType.get());
            }
        }
        return result;
    }

    @Override
    public InputStream openOutputResource(final String name) throws IOException {
        var externalNodeData = m_manager.getExternalOutputs().get(name);
        if (externalNodeData == null) {
            throw new IOException(String.format("No output with identifier %s", name));
        }
        var resource = externalNodeData.getResource();
        if (resource == null) {
            throw new IOException(String.format("No output resource for output with identifier %s", name));
        }
        return resource.toURL().openStream();
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
        if (Boolean.getBoolean("java.awt.headless")) {
            m_manager.getParent().removeProject(m_manager.getID());
        } else {
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
