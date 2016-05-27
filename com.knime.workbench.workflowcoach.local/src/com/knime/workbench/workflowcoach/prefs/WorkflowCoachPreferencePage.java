/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by
 * KNIME.com, Zurich, Switzerland
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
 */
package com.knime.workbench.workflowcoach.prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.knime.core.internal.KNIMEPath;
import org.knime.core.node.NodeFrequencies;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.workflowcoach.NodeRecommendationManager;
import org.osgi.framework.FrameworkUtil;

import com.knime.enterprise.utility.recommendation.WorkspaceAnalyzer;
import com.knime.workbench.workflowcoach.data.WorkspaceTripleProvider;

/**
 * Preference page for the local workflow coache.
 *
 * @author Martin Horn, KNIME.com
 */
public class WorkflowCoachPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
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
                    new FileOutputStream(new File(WorkspaceTripleProvider.WORKSPACE_NODE_TRIPLES_JSON_FILE))) {
                    nf.write(os);
                }
            } catch (XPathExpressionException | IOException ex) {
                return new Status(IStatus.ERROR, FrameworkUtil.getBundle(getClass()).getSymbolicName(),
                    "Error while analysing the workspace: " + ex.getMessage(), ex);
            } finally {
                if (!m_analyseButton.isDisposed()) {
                    Display.getDefault().asyncExec(() -> {
                        m_analyseButton.setEnabled(true);
                        m_analyseButton.setText("    Analyse    ");
                    });
                }
            }
            return Status.OK_STATUS;
        }
    }

    /** The id of this preference page. */
    public static final String ID = "com.knime.workbench.workflowcoach";

    private Button m_checkWorkspaceProvider;

    private Button m_analyseButton;

    /**
     * Creates a new preference page.
     */
    public WorkflowCoachPreferencePage() {
        super("Custom KNIME Workflow Coach Settings");
        setDescription("Configure the Workflow Coach.");
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

        Composite compositeWorkspaceProvider = createComposite(composite);
        Composite smallComp = createComposite(compositeWorkspaceProvider, 2);
        m_checkWorkspaceProvider = new Button(smallComp, SWT.CHECK);
        m_checkWorkspaceProvider.setText("Workspace Node Recommendations");
        m_checkWorkspaceProvider.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        m_checkWorkspaceProvider.addSelectionListener(createUpdateValidStatusSelectionListener());
        m_analyseButton = new Button(smallComp, SWT.PUSH);
        m_analyseButton.setText("    Analyse    ");
        m_analyseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                onAnalyse();
            }
        });
        Label help = new Label(compositeWorkspaceProvider, SWT.NONE);
        help.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        help.setText("Determines the node recommendations from your local workspace.");

        if(!WorkspaceTripleProvider.checkLicense()) {
            m_checkWorkspaceProvider.setEnabled(false);
            m_analyseButton.setEnabled(false);
            Label licenseHint = new Label(compositeWorkspaceProvider, SWT.NONE);
            licenseHint.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            licenseHint.setText("No Personal Productivity License found.");
            licenseHint.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
        }

        initializeValues();

        return composite;
    }

    private void initializeValues() {
        m_checkWorkspaceProvider.setSelection(
            getPreferenceStore().getBoolean(WorkflowCoachPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performOk() {
        //check whether the workspace recommendation file exists
        if(Files.exists(Paths.get(WorkspaceTripleProvider.WORKSPACE_NODE_TRIPLES_JSON_FILE))) {
            setErrorMessage("Please analyse the workspace first.");
            return false;
        }

        //store values
        getPreferenceStore().setValue(WorkflowCoachPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER,
            m_checkWorkspaceProvider.getSelection());
        //reload the statistics
        try {
            NodeRecommendationManager.getInstance().loadStatistics();
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
            .getDefaultBoolean(WorkflowCoachPreferenceInitializer.P_WORKSPACE_NODE_TRIPLE_PROVIDER));
    }

    private Composite createComposite(final Composite parent, final int numColumns) {
        Composite composite = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.numColumns = numColumns;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
        return composite;
    }

    private Composite createComposite(final Composite parent) {
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        Group group = new Group(parent, SWT.NONE);
        group.setLayout(layout);
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        return group;
    }

    private SelectionListener createUpdateValidStatusSelectionListener() {
        return new SelectionListener() {

            @Override
            public void widgetSelected(final SelectionEvent e) {
                setValid(isValid());
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
        Job j = new WorkspaceAnalyzerJob();
        j.setUser(true);
        j.schedule();
    }
}
