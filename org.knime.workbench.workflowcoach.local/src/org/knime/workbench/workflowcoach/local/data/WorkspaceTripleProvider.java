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
 *   Mar 17, 2016 (hornm): created
 */
package org.knime.workbench.workflowcoach.local.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.NodeFrequencies;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeTriple;
import org.knime.workbench.workflowcoach.data.NodeTripleProvider;
import org.knime.workbench.workflowcoach.local.prefs.WorkspaceRecommendationsPreferenceInitializer;
import org.osgi.framework.FrameworkUtil;

/**
 * Reads the node triples from a json file that was generated based on the local workspace.
 *
 * @author Martin Horn, KNIME.com
 */
public class WorkspaceTripleProvider implements NodeTripleProvider {
    private static final ScopedPreferenceStore PREFS = new ScopedPreferenceStore(InstanceScope.INSTANCE,
        FrameworkUtil.getBundle(WorkspaceTripleProvider.class).getSymbolicName());

    /**
     * The triple json-file store within the knime workflow metadata directory.
     */
    public static final Path WORKSPACE_NODE_TRIPLES_JSON_FILE =
        Paths.get(KNIMEConstants.getKNIMEHomeDir(), "workspace_recommendations.json");

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<NodeTriple> getNodeTriples() throws IOException {
        return NodeFrequencies.from(Files.newInputStream(WORKSPACE_NODE_TRIPLES_JSON_FILE)).getFrequencies().stream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Workspace";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Frequency of how often the nodes were used in the workflows of your workspace.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        return PREFS.getBoolean(WorkspaceRecommendationsPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER)
            && Files.exists(WORKSPACE_NODE_TRIPLES_JSON_FILE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<LocalDateTime> getLastUpdate() {
        try {
            if (Files.exists(WORKSPACE_NODE_TRIPLES_JSON_FILE)) {
                return Optional.of(LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(WORKSPACE_NODE_TRIPLES_JSON_FILE).toInstant(), ZoneId.systemDefault()));
            } else {
                return Optional.empty();
            }
        } catch (IOException ex) {
            NodeLogger.getLogger(getClass()).warn(
                "Could not determine last update of '" + WORKSPACE_NODE_TRIPLES_JSON_FILE + "': " + ex.getMessage(),
                ex);
            return Optional.empty();
        }
    }
}
