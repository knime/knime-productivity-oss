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
 */
package org.knime.workbench.workflowcoach.local.prefs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Optional;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.knime.core.internal.KNIMEPath;
import org.knime.core.node.NodeFrequencies;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.recommendation.WorkspaceAnalyzer;
import org.knime.workbench.workflowcoach.NodeRecommendationManager;
import org.knime.workbench.workflowcoach.data.NodeTripleProvider;
import org.knime.workbench.workflowcoach.local.data.WorkspaceTripleProvider;
import org.osgi.framework.FrameworkUtil;

/**
 * Preference page for the local workflow coache.
 *
 * @author Martin Horn, KNIME.com
 */
public class WorkspaceRecommendationsPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    private final class WorkspaceAnalyzerJob extends Job {
        private WorkspaceAnalyzerJob() {
            super("Determine node recommendations from workspace");
        }

        @Override
        protected IStatus run(final IProgressMonitor monitor) {
            try {
                WorkspaceAnalyzer wa = new WorkspaceAnalyzer(KNIMEPath.getWorkspaceDirPath().toPath());
                int count = wa.countWorkflows();
                SubMonitor subMonitor = SubMonitor.convert(monitor, count);
                wa.addProgressListener(s -> {
                    subMonitor.worked(1);
                    subMonitor.setTaskName("Analysing files in workspace: " + s);
                    if(monitor.isCanceled()) {
                        Thread.currentThread().interrupt();
                    }
                });
                wa.analyze();
                NodeFrequencies nf = new NodeFrequencies("KNIME Workspace", wa.getTriplets());
                try (OutputStream os =
                    Files.newOutputStream(WorkspaceTripleProvider.WORKSPACE_NODE_TRIPLES_JSON_FILE)) {
                    nf.write(os);
                }
            } catch (XPathExpressionException | IOException ex) {
                return new Status(IStatus.ERROR, FrameworkUtil.getBundle(getClass()).getSymbolicName(),
                    "Error while analysing the workspace: " + ex.getMessage(), ex);
            } finally {
                Display.getDefault().asyncExec(() -> {
                    if (!m_analyseButton.isDisposed()) {
                        m_analyseButton.setEnabled(m_checkWorkspaceProvider.getSelection());
                        m_analyseButton.setText("    Analyse    ");
                        m_lastUpdate.setText("Analysis finished!");
                    }
                });
            }
            return Status.OK_STATUS;
        }
    }

    /** The id of this preference page. */
    public static final String ID = "org.knime.workbench.workflowcoach.local";

    private Button m_checkWorkspaceProvider;

    private Button m_analyseButton;

    private Label m_lastUpdate;

    private Job m_analyzerJob;

    /**
     * Creates a new preference page.
     */
    public WorkspaceRecommendationsPreferencePage() {
        super("Custom KNIME Workflow Coach Settings");
        setDescription("Enable the Workflow Coach to retrieve recommendations based on your local workspace node usage.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final IWorkbench workbench) {
        IPreferenceStore prefStore =
            new ScopedPreferenceStore(InstanceScope.INSTANCE, FrameworkUtil.getBundle(getClass()).getSymbolicName());
        setPreferenceStore(prefStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Control createContents(final Composite parent) {
        Composite composite = createComposite(parent, 1);

        m_checkWorkspaceProvider = new Button(composite, SWT.CHECK);
        m_checkWorkspaceProvider.setText("Workspace Node Recommendations");
        m_checkWorkspaceProvider.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        m_checkWorkspaceProvider.addSelectionListener(createUpdateValidStatusSelectionListener());

        Composite smallComp = createComposite(composite, 2);
        m_analyseButton = new Button(smallComp, SWT.PUSH);
        m_analyseButton.setText("    Analyse    ");
        m_analyseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                onAnalyse();
            }
        });

        m_lastUpdate = new Label(smallComp, SWT.NONE);


        Label help = new Label(composite, SWT.NONE);
        help.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        help.setText("The Analyze button assesses the node recommendations based on your local workspace.");

        initializeValues();

        return composite;
    }

    private void initializeValues() {
        m_checkWorkspaceProvider.setSelection(getPreferenceStore()
            .getBoolean(WorkspaceRecommendationsPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER));
        m_analyseButton.setEnabled(m_checkWorkspaceProvider.getSelection());

        Optional<Optional<LocalDateTime>> lastUpdate = NodeRecommendationManager.getInstance().getNodeTripleProviders()
                .stream().filter(p -> p instanceof WorkspaceTripleProvider)
                .map(p -> p.getLastUpdate()).findFirst();

        if (lastUpdate.isPresent() && lastUpdate.get().isPresent()) {
            m_lastUpdate
                .setText("Last analysis: " + NodeTripleProvider.LAST_UPDATE_FORMAT.format(lastUpdate.get().get()));
        } else {
            m_lastUpdate.setText("Not analysed yet.");
            m_lastUpdate.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performOk() {
        //check whether the workspace recommendation file exists
        if (!Files.exists(WorkspaceTripleProvider.WORKSPACE_NODE_TRIPLES_JSON_FILE)
            && ((m_analyzerJob == null) || (m_analyzerJob.getState() != Job.RUNNING))) {
            setErrorMessage("Please analyse the workspace first.");
            return false;
        }

        //store values
        getPreferenceStore().setValue(WorkspaceRecommendationsPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER,
            m_checkWorkspaceProvider.getSelection());
        //reload the statistics
        try {
            NodeRecommendationManager.getInstance().loadRecommendations();
            return true;
        } catch (Exception ex) {
            setErrorMessage("Can't load the requested node recommendations. Please see log for details.");
            NodeLogger.getLogger(getClass()).error("Can't load the requested node recommendations: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void performDefaults() {
        //restore default values
        m_checkWorkspaceProvider.setSelection(getPreferenceStore()
            .getDefaultBoolean(WorkspaceRecommendationsPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER));
    }

    private static Composite createComposite(final Composite parent, final int numColumns) {
        Composite composite = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.numColumns = numColumns;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        return composite;
    }

    private SelectionListener createUpdateValidStatusSelectionListener() {
        return new SelectionListener() {

            @Override
            public void widgetSelected(final SelectionEvent e) {
                setValid(isValid());
                m_analyseButton.setEnabled(m_checkWorkspaceProvider.getSelection());
            }

            @Override
            public void widgetDefaultSelected(final SelectionEvent e) {
                setValid(isValid());
            }
        };
    }

    /**
     * Performs the analyze operation and writes the result to a json file.
     */
    private void onAnalyse() {
        m_analyseButton.setEnabled(false);
        m_analyseButton.setText("Analysing...");
        m_lastUpdate.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
        m_analyzerJob = new WorkspaceAnalyzerJob();
        m_analyzerJob.setUser(true);
        m_analyzerJob.schedule();
    }
}
