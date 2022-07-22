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
 *   06.04.2021 (jl): created
 */
package org.knime.workflowservices.knime.caller;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Represents the port assignments for either workflow input or output parameters. Each parameter has an up and a down
 * button that allows assigning it to a different port.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 *
 * @noreference This class is not intended to be referenced by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
final class PanelOrderableParameters {

    /** The inner padding of a column cell. */
    static final int COL_PADDING_INNER = 2;

    /** The panel the whole dialogue is created on. */
    private final JPanel m_panel = new JPanel();

    /** The layout of this component for later access. */
    private final GridBagLayout m_layout = new GridBagLayout();

    /** Contains the rows in this dialog. */
    private final List<PanelWorkflowParameter> m_rows = new ArrayList<>();

    /** An array containing the labels defining the header. */
    private final JLabel m_headerLabel;

    /**
     * Takes the parameter name and port index and generates the text for the source label.
     *
     * For input parameters, this generates something like "Input Port 3 (Data)". For output parameters, this returns
     * just the parameter name.
     */
    private final BiFunction<WorkflowParameter, Integer, String> m_sourceLabel;

    /**
     * Takes the parameter name and port index and generates the text for the target label.
     *
     * For input parameters, this generates outputs the workflow input parameter name. For output parameters, this
     * returns something like "Output Port 2 (Tree Ensembles)"
     */
    private final BiFunction<WorkflowParameter, Integer, String> m_targetLabel;

    /** callback notify the parent panel that the order of the workflow parameters has been changed */
    private final Runnable m_reorderListener;

    /**
     * @param rowOffset the index of the row
     * @param insetsBottom the bottom padding for the element this constrains are applied to
     * @return the {@link GridBagConstraints} that position the names in the second column.
     */
    static GridBagConstraints getAssignmentColumnConstraints(final int rowOffset, final int insetsBottom) {
        final var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.gridy = rowOffset + 1; // skip the header label
        gbc.insets = new Insets(COL_PADDING_INNER, COL_PADDING_INNER, insetsBottom, COL_PADDING_INNER);

        return gbc;
    }

    /**
     * @param rowOffset the index of the row
     * @return the {@link GridBagConstraints} that position the buttons in the forth column.
     */
    static GridBagConstraints getButtonsColumnConstraints(final int rowOffset) {
        final var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 1;
        gbc.gridy = rowOffset + 1; // skip the header label
        gbc.insets = new Insets(COL_PADDING_INNER, COL_PADDING_INNER, COL_PADDING_INNER, 0);

        return gbc;
    }

    /**
     * Creates a new dialogue for the node.
     *
     * @param headerLabel whether this displays input or output parameters
     * @param sourceLabel takes the parameter name and port index and generates the text for the source of the parameter
     *            assignment.
     * @param targetLabel takes the parameter name and port index and generates the text for the target of the parameter
     *            assignment.
     * @param reorderListener callback notify the parent panel that the order of the workflow parameters has been
     *            changed
     */
    PanelOrderableParameters(final String headerLabel,
        final BiFunction<WorkflowParameter, Integer, String> sourceLabel,
        final BiFunction<WorkflowParameter, Integer, String> targetLabel, final Runnable reorderListener) {

        m_reorderListener = reorderListener;

        m_sourceLabel = sourceLabel;
        m_targetLabel = targetLabel;

        m_panel.setBorder(BorderFactory.createTitledBorder(headerLabel));

        m_headerLabel = new JLabel(headerLabel);
        //        panel.setLayout(new GridBagLayout());
        //        final var constraints = new GridBagConstraints();
        //        constraints.gridx = constraints.gridy = 0;
        //        constraints.insets = new Insets(TAB_PADDING, TAB_PADDING, TAB_PADDING, TAB_PADDING);
        //        constraints.fill = GridBagConstraints.BOTH;
        //        constraints.weightx = constraints.weighty = 1.0;
        //        panel.add(m_panel, constraints);
    }

    /**
     * @return the panel the GUI is contained on
     */
    JPanel getGUIPanel() {
        return m_panel;
    }

    /**
     * @return the layout
     */
    GridBagLayout getLayout() {
        return m_layout;
    }

    /**
     * @return the rows in this dialog
     */
    List<PanelWorkflowParameter> getRows() {
        return m_rows;
    }

    /**
     * @return the amount of rows in this dialog. This number may be different from the number of rows in the settings
     *         model if the variables are currently loaded.
     */
    int getRowCount() {
        return m_rows.size();
    }

    /**
     * Add a row to the bottom.
     *
     * @param parameter the name and type of the workflow input/output parameter to display.
     */
    void addRow(final WorkflowParameter parameter) {

        final var row = new PanelWorkflowParameter(this, m_sourceLabel, m_targetLabel, parameter);
        m_rows.add(row);

        // also update the second-last row
        if (m_rows.size() > 1) {
            m_rows.get(m_rows.size() - 2).updateMoveButtons();
        }
    }

    /**
     * Swaps the to rows with the given indices.
     *
     * @param first the first row
     * @param second the second row
     */
    void swapRows(final int first, final int second) {
        Collections.swap(getRows(), first, second);

        // this is after the swap so the positions do not match their indices anymore
        final PanelWorkflowParameter firstRow = getRows().get(first); // old second row
        final PanelWorkflowParameter secondRow = getRows().get(second);// old first  row

        firstRow.setIndex(first);
        secondRow.setIndex(second);

        firstRow.setRowInLayout(first, getLayout());
        secondRow.setRowInLayout(second, getLayout());

        final JPanel parentPanel = getGUIPanel();
        parentPanel.revalidate();
        parentPanel.repaint();

        // notify the parent panel that the order of the workflow parameters has been changed
        m_reorderListener.run();
    }

    /**
     * @return the names of the workflow input/output parameters in the order they are arranged in the GUI using the
     *         up/down buttons
     */
    String[] getParameterOrder() {
        return m_rows.stream().map(PanelWorkflowParameter::getParameterName).toArray(String[]::new);
    }

    /**
     * @param parameters the workflow input or output parameters of the callee workflow
     */
    void update(final List<WorkflowParameter> parameters) {
        m_rows.clear();
        m_panel.removeAll();

        m_panel.setLayout(m_layout);

        // add the heading
        //        if(! parameters.isEmpty()) {
        //            m_panel.add(m_headerLabel);
        //        }

        for (var i = 0; i < parameters.size(); i++) {
            addRow(parameters.get(i));
        }

        if (parameters.isEmpty()) {
            m_panel.add(new JLabel("None"));
        }

        if (!m_rows.isEmpty()) {
            m_rows.get(0).updateMoveButtons();
            m_rows.get(m_rows.size() - 1).updateMoveButtons();
        }

        m_panel.revalidate();
        m_panel.repaint();
    }

}
