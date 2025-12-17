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
 * ------------------------------------------------------------------------
 */

package org.knime.workflowservices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.knime.base.node.io.filehandling.webui.FileChooserPathAccessor;
import org.knime.base.node.io.filehandling.webui.FileSystemPortConnectionUtil;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.util.hub.CurrentState;
import org.knime.core.util.hub.ItemVersion;
import org.knime.core.util.hub.ItemVersionStringPersistor;
import org.knime.core.util.hub.MostRecent;
import org.knime.core.util.hub.SpecificVersion;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelection;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelectionWidget;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSystemOption;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.LegacyReaderFileSelectionPersistor;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.SingleFileSelectionMode;
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.WithFileSystem;
import org.knime.core.webui.node.dialog.defaultdialog.util.updates.StateComputationFailureException;
import org.knime.filehandling.core.connections.FSCategory;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.ItemVersionAware.RepositoryItemVersion;
import org.knime.filehandling.core.connections.RelativeTo;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.migration.LoadDefaultsForAbsentFields;
import org.knime.node.parameters.persistence.NodeParametersPersistor;
import org.knime.node.parameters.persistence.Persistor;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.updates.ValueProvider;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoice;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.workflowservices.CallWorkflowParameters.DependOnFetchConfig;
import org.knime.workflowservices.CommonParameters.IsHubAuthenticatorConnected;
import org.knime.workflowservices.CommonParameters.IsRemoteExecution;
import org.knime.workflowservices.connection.WorkflowExecutionConnector;
import org.knime.workflowservices.connection.util.ConnectionUtil;

/**
 * Parameters for selecting a workflow/deployment and the executor to run it on. Handles both file system-based workflow
 * selection and Hub deployment selection.
 *
 * @author Paul Baernreuther, KNIME GmbH, Germany
 */
@SuppressWarnings("restriction")
@LoadDefaultsForAbsentFields
class RunWorkflowParameters implements NodeParameters {

    interface InputPortGroupNameRef extends ParameterReference<String> {
    }

    @Widget(title = "Workflow", //
        description = "The path to the workflow you want to call.")
    @FileSelectionWidget(SingleFileSelectionMode.WORKFLOW)
    @WithFileSystem({FileSystemOption.CONNECTED, FileSystemOption.SPACE})
    @ValueReference(WorkflowPathRef.class)
    @Persistor(WorkflowPathPersistor.class)
    FileSelection m_workflowPath =
        new FileSelection(new FSLocation(FSCategory.RELATIVE, RelativeTo.SPACE.getSettingsValue(), ""));

    interface WorkflowPathRef extends ParameterReference<FileSelection> {
    }

    static final class WorkflowPathPersistor extends LegacyReaderFileSelectionPersistor {

        WorkflowPathPersistor() {
            super("calleeWorkflow");
        }

    }

    @Widget(title = "Workflow version", //
        description = """
                If available, select the version of the workflow to read. A limited number of versions will be \
                fetched and provided in a dropdown menu. If the desired version is not included in the dropdown \
                list, it can be set with a flow variable (e.g. "333" for version \
                333 on the hub).
                """)
    @ChoicesProvider(WorkflowVersionChoicesProvider.class)
    @Effect(predicate = NoHubAuthenticatorConnectedAndRemoteExecution.class, type = EffectType.SHOW)
    @Persistor(WorkflowVersionPersistor.class)
    @ValueReference(WorkflowVersionRef.class)
    String m_version = WorkflowVersionPersistor.CURRENT_STATE_LINK_TYPE;

    interface WorkflowVersionRef extends ParameterReference<String> {
    }

    @Widget(title = "Execution context", //
        description = """
                Only for workflows on a KNIME Hub. The workflow will be executed in the selected context. \
                An execution context of a hub space defines for instance the installed extensions, \
                available credentials, and the pool from which computation resources are used when the \
                service is invoked.
                """)
    @ChoicesProvider(ExecutionContextChoicesProvider.class)
    @ValueProvider(AutoGuessExecutionContextProvider.class)
    @Effect(predicate = NoHubAuthenticatorConnectedAndRemoteExecution.class, type = EffectType.SHOW)
    @ValueReference(ExecutionContextRef.class)
    String m_executionContext;

    interface ExecutionContextRef extends ParameterReference<String> {
    }

    static final class NoHubAuthenticatorConnectedAndRemoteExecution implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return and(i.getPredicate(IsRemoteExecution.class),
                i.getPredicate(IsHubAuthenticatorConnected.class).negate());
        }

    }

    /**
     * Custom persistor for ItemVersion that converts between String (UI) and ItemVersion (persistence).
     */
    static final class WorkflowVersionPersistor implements NodeParametersPersistor<String> {

        private static final String HUB_ITEM_VERSION_CFG_KEY = "hubItemVersion";

        static final String CURRENT_STATE_LINK_TYPE = "LATEST_STATE";

        private static final String MOST_RECENT_LINK_TYPE = "LATEST_VERSION";

        @Override
        public String load(final NodeSettingsRO settings) throws InvalidSettingsException {
            final var itemVersion = ItemVersionStringPersistor.load(settings).orElse(ItemVersion.currentState());
            return itemVersionToString(itemVersion);
        }

        @Override
        public void save(final String param, final NodeSettingsWO settings) {
            try {
                final var itemVersion = stringToItemVersion(param);
                ItemVersionStringPersistor.save(itemVersion, settings);
            } catch (NumberFormatException e) {
                settings.addString(HUB_ITEM_VERSION_CFG_KEY, param);
            }
        }

        @Override
        public String[][] getConfigPaths() {
            return new String[][]{{HUB_ITEM_VERSION_CFG_KEY}};
        }

        private static String itemVersionToString(final ItemVersion version) {
            if (version instanceof SpecificVersion sv) {
                return sv.getVersionString();
            } else if (version instanceof CurrentState) {
                return CURRENT_STATE_LINK_TYPE;
            } else if (version instanceof MostRecent) {
                return MOST_RECENT_LINK_TYPE;
            }
            throw new IllegalStateException("Unexpected version class: " + version.getClass());
        }

        private static ItemVersion stringToItemVersion(final String versionString) {
            if (CURRENT_STATE_LINK_TYPE.equals(versionString)) {
                return CurrentState.getInstance();
            }
            if (MOST_RECENT_LINK_TYPE.equals(versionString)) {
                return MostRecent.getInstance();
            }
            return new SpecificVersion(Integer.parseUnsignedInt(versionString));
        }
    }

    /**
     * Choices provider for workflow versions. Fetches available versions from Hub when applicable.
     *
     * Although the provided {@link WorkflowExecutionConnector} would also allow for listing the versions, we do not use
     * it here to enable quicker UI response when changing the workflow path. Otherwise, the user would have to wait for
     * all downstream computations of the workflow connection to be finished before the correct versions are being
     * displayed.
     *
     * This implementation is copied from the Workflow Reader node.
     */
    static final class WorkflowVersionChoicesProvider implements StringChoicesProvider {

        private static final String VERSION_SELECTOR_CURRENT_STATE = "Latest edits";

        private static final String VERSION_SELECTOR_LATEST_VERSION = "Latest version";

        private static final Map<String, ItemVersion> DEFAULT_VERSIONS =
            Map.of(VERSION_SELECTOR_CURRENT_STATE, ItemVersion.currentState());

        private Supplier<FileSelection> m_workflowPathSupplier;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_workflowPathSupplier = initializer.computeFromValueSupplier(WorkflowPathRef.class);
            initializer.computeAfterOpenDialog();
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput context) {
            if (IsHubAuthenticatorConnected.isHubAuthenticatorConnected(context)) {
                return List.of();
            }
            final var availableVersions = fetchAvailableVersions(context);
            final var choices = new ArrayList<StringChoice>();
            for (final var entry : availableVersions.entrySet()) {
                choices.add(new StringChoice(itemVersionToString(entry.getValue()), entry.getKey()));
            }
            return choices;
        }

        private Map<String, ItemVersion> fetchAvailableVersions(final NodeParametersInput context) {
            final var workflowPath = m_workflowPathSupplier.get();
            if (workflowPath == null || workflowPath.getFSLocation() == null) {
                return DEFAULT_VERSIONS;
            }
            final var fsConnection = FileSystemPortConnectionUtil.getFileSystemConnection(context);
            try (final var accessor = new FileChooserPathAccessor(workflowPath, fsConnection)) {
                final var paths = accessor.getFSPaths(statusMessage -> {
                });
                if (paths.isEmpty()) {
                    return DEFAULT_VERSIONS;
                }

                final var path = paths.get(0);
                final var hubVersions = getHubVersions(path);
                if (hubVersions.isPresent()) {
                    return hubVersions.get();
                }
            } catch (Exception e) { // NOSONAR
                // This catches exceptions like NoSuchFile / AccessDenied
                // We simply return the default map (only current state)
            }

            return DEFAULT_VERSIONS;
        }

        private static Optional<Map<String, ItemVersion>> getHubVersions(final FSPath path) throws IOException {
            try (var fileSystem = path.getFileSystem()) {
                final var versionAwareOpt = fileSystem.getItemVersionAware();
                if (versionAwareOpt.isPresent()) {
                    final var versionAware = versionAwareOpt.get();
                    final var hubVersions = versionAware.getRepositoryItemVersions(path);
                    if (!hubVersions.isEmpty()) {
                        final var versions = new LinkedHashMap<String, ItemVersion>();
                        versions.put(VERSION_SELECTOR_CURRENT_STATE, ItemVersion.currentState());
                        versions.put(VERSION_SELECTOR_LATEST_VERSION, ItemVersion.mostRecent());
                        hubVersions
                            .forEach(v -> versions.put(makeVersionTitle(v), ItemVersion.of((int)v.getVersion())));
                        return Optional.of(versions);
                    }
                }
            }
            return Optional.empty();
        }

        private static String makeVersionTitle(final RepositoryItemVersion v) {
            return "Version " + v.getVersion() + ": " + v.getTitle();
        }

        private static String itemVersionToString(final ItemVersion version) {
            if (version instanceof SpecificVersion sv) {
                return sv.getVersionString();
            } else if (version instanceof CurrentState) {
                return WorkflowVersionPersistor.CURRENT_STATE_LINK_TYPE;
            } else if (version instanceof MostRecent) {
                return WorkflowVersionPersistor.MOST_RECENT_LINK_TYPE;
            }
            throw new IllegalStateException("Unexpected version class: " + version.getClass());
        }
    }

    /**
     * Choices provider for execution contexts. Fetches available execution contexts from Hub.
     */
    static final class ExecutionContextChoicesProvider extends DependOnFetchConfig<List<StringChoice>>
        implements StringChoicesProvider {

        @Override
        public void additionalInit(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public List<StringChoice> computeState(final NodeParametersInput parametersInput) {
            if (IsHubAuthenticatorConnected.isHubAuthenticatorConnected(parametersInput)) {
                return List.of();
            }
            try {
                final var fetchConfig = getFetchConfig(parametersInput);
                final var connection = ConnectionUtil.createConnection(fetchConfig);
                if (connection.isPresent()) {
                    final var executionContexts = connection.get().getExecutionContexts();
                    final var choices = new ArrayList<StringChoice>();
                    for (final var execContext : executionContexts) {
                        choices.add(new StringChoice(execContext.id(), execContext.name()));
                    }
                    return choices;
                }
                return List.of();
            } catch (Exception e) { // NOSONAR
                return List.of();
            }
        }
    }

    static final class AutoGuessExecutionContextProvider implements StateProvider<String> {

        private Supplier<List<StringChoice>> m_choices;

        private Supplier<String> m_currentValue;

        @Override
        public void init(final StateProviderInitializer initializer) {
            m_choices = initializer.computeFromProvidedState(ExecutionContextChoicesProvider.class);
            m_currentValue = initializer.getValueSupplier(ExecutionContextRef.class);
        }

        @Override
        public String computeState(final NodeParametersInput context) throws StateComputationFailureException {
            final var currentValue = m_currentValue.get();
            if (currentValue != null && !currentValue.isEmpty()) {
                throw new StateComputationFailureException();
            }
            final var choices = m_choices.get();
            if (choices.isEmpty()) {
                throw new StateComputationFailureException();
            }
            return choices.get(0).id();
        }
    }

}
