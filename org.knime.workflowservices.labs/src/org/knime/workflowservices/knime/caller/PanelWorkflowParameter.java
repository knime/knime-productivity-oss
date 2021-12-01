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
 *   12.04.2021 (jl): created
 */
package org.knime.workflowservices.knime.caller;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.function.BiFunction;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.node.util.SharedIcons;

/**
 * Shows an assignment between workflow parameter and Call Workflow Service node port. The order of rows can be changed
 * to control the order of the ports on the Call Workflow node.
 *
 * This class is used to show input and output parameter assignments. For input parameters, the source will be a port
 * and the target will be an input parameter of the workflow. For output parameters, the source will be an output
 * parameter and the target will be an output port.
 *
 * The panels are directly inserted into the parent
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
final class PanelWorkflowParameter {

    /** The identifier of the workflow input/output parameter whose assignment is represented by this GUI element. */
    private final WorkflowParameter m_parameter;

    /** The offset of the port (0 = first port) to which the workflow parameter {@link #m_parameter} is assigned. */
    private int m_index;

    /**
     * The component on which this row is contained. {@link PanelWorkflowParameters} has two, one for input and one for
     * output parameters.
     */
    private final PanelOrderableParameters m_parent;

    /** Shows the name of the workflow parameter (e.g., "prediction-input") */
    private final JLabel m_sourceLabel;

    /** Shows the index and type of the assigned port  */
    private final JLabel m_targetLabel;

    /** Shows which source (parameter/port) {@link #m_sourceLabel} is assigned to which target (port/parameter) {@link #m_targetLabel}. */
    private final JPanel m_assignmentPanel;

    /** The button that moves this row up. */
    private final JButton m_up;

    /** The button that moves this row down. */
    private final JButton m_down;

    /** The panel containing both {@link #m_up},{@link #m_down} and {@link #m_delete}. */
    private final JPanel m_buttonBox;

    private BiFunction<WorkflowParameter, Integer, String> m_sourceLabeller;

    private BiFunction<WorkflowParameter, Integer, String> m_targetLabeller;

    /**
     * Creates a new row in the panel of the dialog.
     *
     * @param parent the dialog panel this row belongs to (is assumed to be contained in a {@link GridBagLayout})
     * @param parameter the identifier of the workflow input/output parameter whose assignment is represented by this
     *            GUI element
     */
    PanelWorkflowParameter(final PanelOrderableParameters parent,
        final BiFunction<WorkflowParameter, Integer, String> sourceLabeller,
        final BiFunction<WorkflowParameter, Integer, String> targetLabeller, final WorkflowParameter parameter) {

        m_parent = parent;
        m_sourceLabeller = sourceLabeller;
        m_targetLabeller = targetLabeller;

        m_parameter = parameter;
        m_index = m_parent.getRows().size();

        m_sourceLabel = new JLabel();
        m_targetLabel = new JLabel();
        m_assignmentPanel = new JPanel();

        m_up = new JButton();
        m_down = new JButton();
        m_buttonBox = new JPanel();

        initRow();
        // refresh labels and buttons
        setIndex(m_index);
    }

    /**
     * Initiates new row in the dialog
     */
    private void initRow() {
        final JPanel parentPanel = m_parent.getGUIPanel();

        // we create this in reverse order to ensure correct checks
        parentPanel.add(makeAssignmentPanel(), PanelOrderableParameters.getAssignmentColumnConstraints(m_index,
            PanelOrderableParameters.COL_PADDING_INNER));
        parentPanel.add(makeButtons(), PanelOrderableParameters.getButtonsColumnConstraints(m_index));
    }

    /**
     * @return the panel that shows which input port/output parameter is assigned to which input parameter/output port.
     */
    private JPanel makeAssignmentPanel() {
        JPanel panel = m_assignmentPanel;
        panel.setLayout(new GridBagLayout());

        final var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridx = 0;
        panel.add(m_sourceLabel, c);

        c.gridx = 1;
        panel.add(new JLabel(" assigned to "), c);

        c.gridx = 2;
        panel.add(m_targetLabel, c);

        return panel;
    }

    /**
     * @return the name of the workflow input/output parameter this GUI element shows the port assignment for
     */
    String getParameterName() {
        return m_parameter.getParameterName();
    }

    /**
     * @return the index of this row
     */
    int getIndex() {
        return m_index;
    }

    /**
     * This will change the labels, because the index defines the port index the parameter is mapped to. Also, the
     * whether move up or down buttons are enables depends on whether this is the first or last parameter assignment.
     *
     * @param index the offset in the list of parameter port assignments
     */
    void setIndex(final int index) {
        m_index = index;
        m_sourceLabel.setText(m_sourceLabeller.apply(m_parameter, m_index));
        m_targetLabel.setText(m_targetLabeller.apply(m_parameter, m_index));
        updateMoveButtons();
    }

    /**
     * Create a panel containing the three buttons used to move this row up, down, and delete it.
     *
     * @return the newly created panel (which is also saved in {@link #m_buttonBox}).
     */
    private JPanel makeButtons() {
        m_buttonBox.setLayout(new BoxLayout(m_buttonBox, BoxLayout.X_AXIS));
        final JButton up = m_up;
        up.setIcon(SharedIcons.MOVE_UP.get());
        up.setToolTipText("Move this port up");

        final JButton down = m_down;
        down.setIcon(SharedIcons.MOVE_DOWN.get());
        down.setToolTipText("Move this port down");

        updateMoveButtions(m_index);

        // make sure that the icons buttons are square
        final var pref = new Dimension(up.getPreferredSize());
        pref.width = Math.max(pref.height, down.getPreferredSize().height);
        up.setPreferredSize(pref);
        down.setPreferredSize(pref);

        m_buttonBox.add(up);
        m_buttonBox.add(down);

        up.addActionListener(l -> m_parent.swapRows(m_index, m_index - 1));
        down.addActionListener(l -> m_parent.swapRows(m_index, m_index + 1));
        return m_buttonBox;
    }

    /**
     * Moves this {@link PanelWorkflowParameter} to a specific row in the {@link GridBagLayout} of the
     * parent.
     *
     * @param row the row number to move to
     */
    void setRowInLayout(final int row, final GridBagLayout parentLayout) {
        parentLayout.setConstraints(m_assignmentPanel,
            PanelOrderableParameters.getAssignmentColumnConstraints(row, PanelOrderableParameters.COL_PADDING_INNER));
        parentLayout.setConstraints(m_buttonBox, PanelOrderableParameters.getButtonsColumnConstraints(row));
    }

    /**
     * Update the usable state of the move buttons this row.
     */
    void updateMoveButtons() {
        updateMoveButtions(m_index);
    }

    /**
     * Update the usable state of the move buttons of the row at index {@code index}.
     *
     * @param index the index of the row. The method will do nothing if this value is out of bounds.
     */
    private void updateMoveButtions(final int index) {
        final int maxIdx = m_parent.getRows().size() - 1;
        if (index < 0 || index > maxIdx) {
            return;
        }

        final PanelWorkflowParameter buttons = m_parent.getRows().get(index);
        buttons.m_up.setEnabled(index != 0);
        buttons.m_down.setEnabled(index != maxIdx);
    }
}
