/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
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
 *   16.03.2016 (thor): created
 */
package org.knime.core.node.recommendation;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.knime.core.node.NodeFrequencies;
import org.knime.core.node.NodeInfo;
import org.knime.core.node.NodeTriple;
import org.knime.core.node.config.base.XMLConfigEntityResolver;
import org.knime.core.util.PathFilter;
import org.knime.core.util.PathFilters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Analyzes all workflows in the given workspace/directory and creates a predecessor-node-successor table with
 * frequencies.
 *
 * @author Thorsten Meinl, KNIME.com, Zurich, Switzerland
 * @deprecated This class is a copied from org.knime.core.util and will be removed in future versions of KNIME
 */
@Deprecated
public class WorkspaceAnalyzer {
    //
    private final Path m_root;

    private final XPathExpression m_connectionXpath;

    private final XPathExpression m_sourceIdXpath;

    private final XPathExpression m_destIdXpath;

    private final XPathExpression m_nodeFileXpath;

    private final XPathExpression m_nodeFactoryXpath;

    private final XPathExpression m_nodeNameXpath;

    private final XPathExpression m_nodeTypeXpath;

    private final XPathExpression m_nodeFactoryOldXpath;

    private final XPathExpression m_nodeNameOldXpath;

    private final XPathExpression m_isMetanodeOldXpath;

    private final XPathExpression m_subnodeNameXpath;

    private final Map<NodeTriple, NodeTriple> m_triplets = new HashMap<>();

    private List<Consumer<String>> m_listeners = new ArrayList<Consumer<String>>();

    private static class NodePair {
        final String id1, id2;

        final NodeInfo node1, node2;

        NodePair(final String id1, final NodeInfo node1, final String id2, final NodeInfo node2) {
            this.id1 = id1;
            this.node1 = node1;
            this.id2 = id2;
            this.node2 = node2;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id1 == null) ? 0 : id1.hashCode());
            result = prime * result + ((id2 == null) ? 0 : id2.hashCode());
            return result;
        }

        /**
         * {@inheritDoc}
         */
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
            NodePair other = (NodePair)obj;
            return Objects.equals(id1, other.id1) && Objects.equals(id2, other.id2);
        }
    }

    private static DocumentBuilder createParser() throws ParserConfigurationException {
        DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        parser.setEntityResolver(XMLConfigEntityResolver.getInstance());
        return parser;
    }

    /**
     * Creates a new analyzer.
     *
     * @param root the directory from which the analysis should be started
     * @throws XPathExpressionException if an XPath is invalid (does not happen in real life)
     */
    public WorkspaceAnalyzer(final Path root) throws XPathExpressionException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Given path '" + root + "' is not a directory");
        }
        m_root = root;
        XPathFactory fac = XPathFactory.newInstance();
        m_connectionXpath = fac.newXPath().compile("/config/config[@key = 'connections']/config");
        m_sourceIdXpath = fac.newXPath().compile("entry[@key = 'sourceID']/@value");
        m_destIdXpath = fac.newXPath().compile("entry[(@key = 'destID') or (@key = 'targetID')]/@value");
        m_nodeFileXpath = fac.newXPath().compile("entry[@key = 'node_settings_file']/@value");
        m_nodeTypeXpath = fac.newXPath().compile("entry[@key = 'node_type']/@value");
        m_nodeFactoryXpath = fac.newXPath().compile("/config/entry[@key = 'factory']/@value");
        m_nodeNameXpath = fac.newXPath().compile("/config/entry[@key = 'node-name']/@value");
        m_subnodeNameXpath = fac.newXPath().compile("/config/entry[@key = 'name']/@value");

        m_nodeFactoryOldXpath = fac.newXPath().compile("entry[@key = 'factory']/@value");
        m_nodeNameOldXpath = fac.newXPath().compile("entry[@key = 'name']/@value");
        m_isMetanodeOldXpath = fac.newXPath().compile("entry[@key = 'node_is_meta']/@value");
    }

    /**
     * Analyzes the directory.
     *
     * @throws IOException if an I/O error occurs while scanning the directory
     */
    public void analyze() throws IOException {
        analyze(PathFilters.acceptAll);
    }

    /**
     * Analyzes the directory.
     *
     * @param filter a filter for directories that should be excluded during traversal
     * @throws IOException if an I/O error occurs while scanning the directory
     */
    public void analyze(final PathFilter filter) throws IOException {
        Files.walkFileTree(m_root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                throws IOException {
                if (filter.accept(dir)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if ("workflow.knime".equals(file.getFileName().toString()) && attrs.isRegularFile()) {
                    try {
                        m_listeners.stream().forEach(c -> c.accept(file.toString()));
                        analyzeWorkflow(file);
                    } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException ex) {
                        Logger.getLogger(WorkspaceAnalyzer.this.getClass())
                            .error("Errow while analyzing workflow file '" + file + "': " + ex.getMessage(), ex);
                    }
                }
                return Thread.currentThread().isInterrupted() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Counts the number of workflows by counting the number of workflow.kime files.
     *
     * @return the number of workflows
     * @throws IOException if an I/O error occurs while scanning the directory
     */
    public int countWorkflows() throws IOException {
        return countWorkflows(PathFilters.acceptAll);
    }

    /**
     * Counts the number of workflows by counting the number of workflow.kime files.
     *
     * @param filter a filter for directories that should be excluded during traversal
     *
     * @return the number of workflows
     * @throws IOException if an I/O error occurs while scanning the directory
     */
    public int countWorkflows(final PathFilter filter) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        Files.walkFileTree(m_root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                throws IOException {
                if (filter.accept(dir)) {
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if ("workflow.knime".equals(file.getFileName().toString()) && attrs.isRegularFile()) {
                    count.incrementAndGet();
                }
                return Thread.currentThread().isInterrupted() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    /**
     * Adds a progress listener that gets informed about every workflow that is currently processed within the
     * {@link #analyze()} method.
     *
     * @param listener the listener
     */
    public void addProgressListener(final Consumer<String> listener) {
        m_listeners.add(listener);
    }

    void analyzeWorkflow(final Path workflowFile)
        throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        Document doc = createParser().parse(workflowFile.toFile());

        XPath xpath = XPathFactory.newInstance().newXPath();
        Set<NodePair> pairs = new HashSet<>();

        NodeList connections = (NodeList)m_connectionXpath.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < connections.getLength(); i++) {
            Element conn = (Element)connections.item(i);
            analyzeConnection(conn, workflowFile.getParent(), xpath, pairs);
        }

        processTriplets(pairs);
    }

    private void processTriplets(final Collection<NodePair> pairs) {
        for (NodePair p2 : pairs) {
            // find predecessors for pair 2: p1.id1 -- (p1.id2 == p2.id1) -- p2.id2
            List<NodePair> collect = pairs.stream().filter(p1 -> p1.id2.equals(p2.id1)).collect(Collectors.toList());
            if (collect.isEmpty()) {
                m_triplets.computeIfAbsent(new NodeTriple(null, p2.node1, p2.node2), k -> k).incrementCount();
            } else {
                collect.stream().map(p1 -> new NodeTriple(p1.node1, p1.node2, p2.node2))
                    .forEach(t -> m_triplets.computeIfAbsent(t, k -> k).incrementCount());
            }
        }
    }

    private void analyzeConnection(final Element conn, final Path dir, final XPath xpath,
        final Collection<NodePair> pairs)
            throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        String sourceId = (String)m_sourceIdXpath.evaluate(conn, XPathConstants.STRING);
        String destId = (String)m_destIdXpath.evaluate(conn, XPathConstants.STRING);
        if ("-1".equals(sourceId) || "-1".equals(destId)) {
            return;
        }

        Element sourceNode = (Element)xpath.evaluate(
            "/config/config[@key = 'nodes']/config[entry[@key = 'id' and @value = '" + sourceId + "']]",
            conn.getOwnerDocument(), XPathConstants.NODE);

        Element destNode = (Element)xpath.evaluate(
            "/config/config[@key = 'nodes']/config[entry[@key = 'id' and @value = '" + destId + "']]",
            conn.getOwnerDocument(), XPathConstants.NODE);

        Optional<NodeInfo> sourceNodeIdentifier = getNodeIdentifier(sourceNode, dir);
        Optional<NodeInfo> destNodeIdentifier = getNodeIdentifier(destNode, dir);
        if (sourceNodeIdentifier.isPresent() && destNodeIdentifier.isPresent()) {
            pairs.add(new NodePair(sourceId, sourceNodeIdentifier.get(), destId, destNodeIdentifier.get()));
        }
    }

    private Optional<NodeInfo> getNodeIdentifier(final Element node, final Path dir)
        throws XPathExpressionException, SAXException, IOException, ParserConfigurationException {
        String nodeType = (String)m_nodeTypeXpath.evaluate(node, XPathConstants.STRING);
        boolean isOldMetanode = (Boolean)m_isMetanodeOldXpath.evaluate(node, XPathConstants.BOOLEAN);
        if (nodeType.isEmpty() && !isOldMetanode) {
            String factoryClass = (String)m_nodeFactoryOldXpath.evaluate(node, XPathConstants.STRING);
            String nodeName = (String)m_nodeNameOldXpath.evaluate(node, XPathConstants.STRING);
            return Optional.of(new NodeInfo(factoryClass, nodeName));
        } else if ("NativeNode".equals(nodeType)) {
            String relativeNodeFile = (String)m_nodeFileXpath.evaluate(node, XPathConstants.STRING);
            Path nodeFile = dir.resolve(relativeNodeFile);
            if (Files.exists(nodeFile)) {
                Document doc = createParser().parse(nodeFile.toFile());
                String factoryClass = (String)m_nodeFactoryXpath.evaluate(doc, XPathConstants.STRING);
                String nodeName = (String)m_nodeNameXpath.evaluate(doc, XPathConstants.STRING);
                return Optional.of(new NodeInfo(factoryClass, nodeName));
            }
        } else if ("SubNode".equals(nodeType) || "MetaNode".equals(nodeType)) {
            String relativeNodeFile = (String)m_nodeFileXpath.evaluate(node, XPathConstants.STRING);
            Path workflowFile = dir.resolve(relativeNodeFile).getParent().resolve("workflow.knime");
            if (Files.exists(workflowFile)) {
                Document doc = createParser().parse(workflowFile.toFile());
                String nodeName = (String)m_subnodeNameXpath.evaluate(doc, XPathConstants.STRING);
                return Optional.of(new NodeInfo(nodeType, nodeName));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a map with the discovered triplets and their frequencies. The middle part of the triplet is a node, the
     * left part (which may be <code>null</code>) it's predecessor, and the right part its successor.
     *
     * @return a map with triplets and frequencies; never <code>null</code>
     */
    public Collection<NodeTriple> getTriplets() {
        return Collections.unmodifiableCollection(m_triplets.values());
    }

    /**
     * Guess what...
     *
     * @param args command line arguments; first is root directory, second the output file
     * @throws Exception if an error occurs
     */
    public static void main(final String[] args) throws Exception {
        WorkspaceAnalyzer analyzer = new WorkspaceAnalyzer(Paths.get(args[0]));
        analyzer.analyze();
        Path outputFile = Paths.get(args[1]);
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            new NodeFrequencies("Custom", analyzer.getTriplets()).write(out);
        }

        try (InputStream is = Files.newInputStream(outputFile)) {
            NodeFrequencies nf = NodeFrequencies.from(is);
            nf.toString();
        }
    }
}
