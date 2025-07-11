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
package org.knime.workflowservices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.NodeMessage;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.contextv2.AnalyticsPlatformExecutorInfo;
import org.knime.core.node.workflow.contextv2.HubSpaceLocationInfo;
import org.knime.core.node.workflow.contextv2.RestLocationInfo;
import org.knime.core.node.workflow.contextv2.ServerLocationInfo;
import org.knime.core.node.workflow.contextv2.WorkflowContextV2;
import org.knime.core.util.FileUtil;
import org.knime.core.util.KNIMETimer;
import org.knime.core.util.LockFailedException;
import org.knime.core.util.Pair;
import org.knime.core.util.ThreadLocalHTTPAuthenticator;
import org.knime.core.util.URIUtil;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.knime.core.util.report.ReportingConstants;
import org.knime.core.util.report.ReportingConstants.RptOutputFormat;
import org.knime.gateway.impl.project.Project;
import org.knime.gateway.impl.project.ProjectManager;
import org.knime.gateway.impl.project.WorkflowServiceProjects;
import org.knime.reporting.executor.ReportExecutor;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.ExplorerURLStreamHandler;
import org.knime.workbench.ui.navigator.ProjectWorkflowMap;
import org.knime.workflowservices.json.row.caller.local.CallLocalWorkflowNodeFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;

import jakarta.json.JsonValue;

/**
 * A local workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 *
 * This backend is also used by deprecated nodes, so any changes must either be backward compatible to avoid breaking
 * {@link CallLocalWorkflowNodeFactory} (the Call Local Workflow (Row Based) node) and Pre43CallWorkflowTableNodeModel
 * (the deprectated Call Workflow (Table Based) node).
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public final class LocalWorkflowBackend implements IWorkflowBackend {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(LocalWorkflowBackend.class);

    private static final Cache<URI, LocalWorkflowBackend> CACHE =
        CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.MINUTES).maximumSize(5)
            .removalListener((final RemovalNotification<URI, LocalWorkflowBackend> notification) -> {
                final var value = notification.getValue();
                if (value.isInUse()) {
                    value.setDiscardAfterUse();
                } else {
                    value.discard();
                }
            }).build();

    static {
        WorkflowServiceProjects.setOnRemoveAllProjectsCallback(CACHE::invalidateAll);
    }

    private static final Map<WorkflowManager, Set<URI>> CALLER_MAP = new WeakHashMap<>();

    /**
     * Creates a new local workflow backend.
     *
     * In case the given path is resolved to a http URL, the workflow is downloaded and extracted.
     *
     * @param path to the workflow: either a path or a knime uri. Unfortunately this defines a legacy format where paths
     *            starting with "/" are interpreted as relative to the current mount point (typically "LOCAL") and all
     *            other paths are interpreted as workflow relative paths. For example,
     *            <li>"/callee" is interpreted as "knime://knime.mountpoint/callee"</li>
     *            <li>"callee" is interpreted as "knime://knime.workflow/callee"</li> The method also accepts knime uris
     *            directly (as the ones just described) or others, e.g.,
     *            <li>"knime://someMountPointName/callee"</li> which is a mount point-absolute uri referring to the
     *            mount point named someMountPointName.
     * @param callingWorkflow the calling workflow
     * @return a new local workflow backend
     * @throws Exception if the path does not point to a workflow
     */
    public static LocalWorkflowBackend newInstance(final String path, final WorkflowManager callingWorkflow)
        throws Exception {
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

        // resolve relative URLs into absolute URLs, usually either file or http, may also return a KNIME URI in some
        // legacy code paths
        Path workflowDir = null;
        URL resolvedUrl = null;
        final boolean deleteAfterUse;
        try {
            resolvedUrl = ExplorerURLStreamHandler.resolveKNIMEURL(originalUrl);

            if (resolvedUrl.getProtocol().equalsIgnoreCase("file")) {
                workflowDir = FileUtil.resolveToPath(resolvedUrl);
                deleteAfterUse = false;
            } else if (resolvedUrl.getProtocol().equalsIgnoreCase("knime")) {
                // ExplorerStreamHandler cannot handle some mount point absolute uris, e.g.,
                // knime://knime-teamspace/OS/Callee, it will just return the input unchanged. In this case, the
                // resolver util can help (but applying it in the first place would cause compatibility isses because it
                // copies temporary files into different locations). the resolver util expects an encoded URI
                // (e.g., it throws an exception if given a URI containing spaces)
                var encodedUri = URIUtil.createEncodedURI(originalUrl).orElseThrow(() -> new IllegalArgumentException(
                    String.format("Invalid callee location, \"%s\" cannot be converted to URI.", path)));
                workflowDir = ResolverUtil.resolveURItoLocalFile(encodedUri).toPath();
                deleteAfterUse = false;
            } else {
                assert resolvedUrl.getProtocol().startsWith("http") : "Expected http URL but not " + resolvedUrl;
                workflowDir = downloadAndExtractRemoteWorkflow(originalUrl);
                deleteAfterUse = true;
            }
        } catch (IOException e) {
            if (e.getMessage().contains("Server returned HTTP response code: 403")) {
                throw new IOException(
                    "User does not have permissions to read workflow " + originalUrl + " on the server", e);
            } else {
                throw e;
            }
        }

        if (!Files.isDirectory(workflowDir)) {
            throw new IOException("No such workflow: " + workflowDir);
        }

        // Converting the path directly to URI leads to key matching problems in the ProjectWorkflowMap as three
        // slashes will be added after the scheme part of the URI, however the value will be accessed with only one slash
        // after the scheme. Hence, convert to file first. Furthermore, keys will be accessed with normalized URIs. See AP-7589.
        var localUri = workflowDir.toFile().toURI().normalize();
        var ou = originalUrl; // Just to make the compiler happy
        final var localWorkflowBackend = CACHE.get(localUri, () -> loadWorkflow(localUri, ou));
        localWorkflowBackend.lock();

        localWorkflowBackend.m_deleteAfterUse = deleteAfterUse;

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
        var file = new File(localUri);

        CheckUtils.checkState(NodeContext.getContext() != null, "NodeContext not set while loading callee workflow");
        final var callerContext = NodeContext.getContext().getWorkflowManager().getContextV2();
        var execInfo = callerContext.getExecutorInfo();

        if (Boolean.getBoolean("java.awt.headless")) {
            // running as an executor on the server or as batch executor

            final var location = callerContext.getLocationInfo();
            //ctxFac = callerContext.createCopy();

            WorkflowContextV2 ctx;
            /* This 'if' block is _only_ relevant for the "Call Local Workflow" node, deprecated since 4.7 (Dec '22).
             * All other new implementations use org.knime.workflowservices.connection.ServerConnectionUtil, which
             * will facilite a RunningOnServerItselfServerConnection, which will instantiate a real server connection.
             */
            // compute the new path in the server repository base on the caller's path and the URL type
            if (location instanceof RestLocationInfo remoteInfo) {
                // callerContext.getRemoteRepositoryAddress().isPresent() && callerContext.getRelativeRemotePath().isPresent()
                String relPath;
                if (ExplorerURLStreamHandler.WORKFLOW_RELATIVE.equalsIgnoreCase(originalUrl.getHost())) {
                    // relPath = callerContext.getRelativeRemotePath().get() + "/" + originalUrl.getPath();
                    relPath = remoteInfo.getWorkflowPath() + "/" + originalUrl.getPath();
                } else if (ExplorerURLStreamHandler.MOUNTPOINT_RELATIVE.equalsIgnoreCase(originalUrl.getHost())) {
                    relPath = originalUrl.getPath();
                } else {
                    // this shouldn't happen
                    relPath = originalUrl.getPath();
                }

                relPath = FilenameUtils.normalize(relPath, true);
                final RestLocationInfo targetLocation;
                if (remoteInfo instanceof ServerLocationInfo) {
                    targetLocation =
                        ServerLocationInfo.builder().withRepositoryAddress(remoteInfo.getRepositoryAddress())
                            .withWorkflowPath(relPath).withAuthenticator(remoteInfo.getAuthenticator())
                            .withDefaultMountId(remoteInfo.getDefaultMountId()).build();
                } else if (remoteInfo instanceof HubSpaceLocationInfo) {
                    throw new IllegalStateException(
                        "Hub Execution not supported for deprecated 'Call Local Workflow' node, update the calling "
                            + "workflow to use a non-deprecated variant.");
                } else {
                    throw new IllegalStateException(
                        "Location info of type " + remoteInfo.getClass().getName() + " not implemented");
                }

                ctx = WorkflowContextV2.builder().withExecutor(execInfo).withLocation(targetLocation).build();
            } else {
                ctx = WorkflowContextV2.forTemporaryWorkflow(file.toPath(), null);
            }
            final var loadResult =
                WorkflowManager.loadProject(file, new ExecutionMonitor(), new WorkflowLoadHelper(ctx));
            return new LocalWorkflowBackend(localUri, loadResult.getWorkflowManager());
        } else {
            // running in GUI mode

            // classic UI
            var wfm = (WorkflowManager)ProjectWorkflowMap.getWorkflow(localUri);
            if (wfm == null) {
                // modern UI
                wfm = WorkflowServiceProjects.getProjectIdAt(file.toPath()) //
                    .flatMap(id -> ProjectManager.getInstance().getProject(id)) //
                    .flatMap(Project::getWorkflowManagerIfLoaded).orElse(null);
            }

            CheckUtils.checkState(execInfo instanceof AnalyticsPlatformExecutorInfo, "Not running in an instance of %s",
                AnalyticsPlatformExecutorInfo.class.getName());
            if (wfm == null) {
                // two cases really:
                // - regular open in KNIME GUI -- mount table is present and "ExplorerFileStore" can be located
                // - test cases: mimic mount table by setting mountpoint (id and path) for callee
                var mpOptional = ((AnalyticsPlatformExecutorInfo)execInfo).getMountpoint();
                final var localWorkflowPath = file.toPath();
                final var fs = ExplorerMountTable.getFileSystem().fromLocalFile(file);
                final var mountpoint = fs == null
                    ? mpOptional.map(p -> Pair.create(p.getFirst().getAuthority(), p.getSecond())).orElse(null)
                    : Pair.create(fs.getMountID(), fs.getContentProvider().getRootStore().toLocalFile().toPath());
                final var ctx = WorkflowContextV2.builder().withAnalyticsPlatformExecutor(exec -> {
                    final var exec2 = exec.withCurrentUserAsUserId().withLocalWorkflowPath(localWorkflowPath);
                    if (mountpoint != null) {
                        exec2.withMountpoint(mountpoint.getFirst(), mountpoint.getSecond());
                    }
                    return exec2;
                }).withLocalLocation().build();
                var loadResult = WorkflowManager.loadProject(file, //
                    new ExecutionMonitor(), //
                    new WorkflowLoadHelper(ctx) //
                );
                wfm = loadResult.getWorkflowManager();

                // classic UI
                ProjectWorkflowMap.putWorkflow(localUri, wfm);

                // modern UI
                WorkflowServiceProjects.registerProject(wfm);

            }
            var localWorkflowBackend = new LocalWorkflowBackend(localUri, wfm);
            ProjectWorkflowMap.registerClientTo(localUri, localWorkflowBackend);
            return localWorkflowBackend;
        }
    }

    private static Path downloadAndExtractRemoteWorkflow(final URL url) throws IOException {
        var tempDir = FileUtil.createTempDir("Called-workflow");
        var zippedWorkflow = new File(tempDir, "workflow.knwf");

        try (final var c = ThreadLocalHTTPAuthenticator.suppressAuthenticationPopups()) {
            final var connection = URLConnectionFactory.getConnection(url);
            if (connection instanceof HttpURLConnection httpConnection && httpConnection.getResponseCode() == 403) {
                httpConnection.disconnect();
                throw new IOException("User does not have permissions to read workflow " + url + " on the server");
            }

            try (OutputStream os = new FileOutputStream(zippedWorkflow); var is = connection.getInputStream()) {
                IOUtils.copy(is, os);
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
            var workflowsUsedBy = CALLER_MAP.remove(callingWorkflow);
            if (workflowsUsedBy != null) {
                for (var workflowUri : workflowsUsedBy) {
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

        for (var e : m_manager.getExternalOutputs().entrySet()) {
            var json = e.getValue().getJSONValue();
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
        return executeAsWorkflowService(input);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowState executeAsWorkflowService(final Map<String, ExternalNodeData> input) throws Exception {
        updateWorkflow(input);
        m_manager.executeAllAndWaitUntilDone();
        var state = m_manager.getNodeContainerState();
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
        var errors = m_manager.getNodeMessages(NodeMessage.Type.ERROR);
        if (!errors.isEmpty()) {
            var b = new StringBuilder();
            for (var p : errors) {
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(p.getFirst()).append(": ").append(p.getSecond().getMessage());
            }
            return b.toString();
        }
        var warnings = m_manager.getNodeMessages(NodeMessage.Type.WARNING);
        if (!warnings.isEmpty()) {
            var b = new StringBuilder();
            for (var p : warnings) {
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
            KNIMETimer.getInstance().schedule(new CacheCleanUpTask(), TimeUnit.SECONDS.toMillis(65L));
        } finally {
            m_inUse.unlock();
        }
    }

    /**
     * @implNote It is essential that this remains a static inner class and is not inlined as an inner class / anonymous
     *           class / lambda. These will have an implicit reference to the outer class ({@code this$0}). Here, the
     *           outer class ({@code LocalWorkflowBackend}) in turn has a reference {@code m_manager} to the
     *           WorkflowManager instance. In effect, this means that the WorkflowManager instance can not be
     *           garbage-collected while the TimerTask instance is alive. In other words, a call to {@code close} would
     *           force the wfm instance to be kept alive for the timer duration, no matter whether it is still in the
     *           {@link LocalWorkflowBackend#CACHE}.
     */
    private static class CacheCleanUpTask extends TimerTask {
        @Override
        public void run() {
            CACHE.cleanUp();
        }
    }

    void discard() {
        if (Boolean.getBoolean("java.awt.headless")) {
            m_manager.getParent().removeProject(m_manager.getID());
        } else {
            // classic UI
            ProjectWorkflowMap.unregisterClientFrom(m_uri, this);
            ProjectWorkflowMap.remove(m_uri);

            // modern UI
            WorkflowServiceProjects.removeProject(new File(m_uri).toPath());
        }
        if (m_deleteAfterUse) {
            FileUtil.deleteRecursively(new File(m_uri));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws ReportGenerationException
     */
    @Override
    public byte[] generateReport(final RptOutputFormat format) throws ReportGenerationException {
        File reportDocDir = null;
        try {
            LOGGER.debug("Starting report document generation for workflow \"" + m_manager.getName() + "\".");
            reportDocDir = ReportExecutor.runReport(m_manager);
        } catch (EngineException e) {
            throw new ReportGenerationException("The generation of the report document failed.", e);
        } catch (IOException e) {
            throw new ReportGenerationException(
                "Reading the report design file or writing the report document failed: " + e.getMessage(), e);
        }
        try {
            var report =
                ReportExecutor.renderReport(m_manager, reportDocDir, format, new ReportingConstants.RptOutputOptions());
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadWorkflow() throws Exception {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateWorkflow(final Map<String, ExternalNodeData> input) throws Exception {
        m_manager.setInputNodes(input);
    }
}
