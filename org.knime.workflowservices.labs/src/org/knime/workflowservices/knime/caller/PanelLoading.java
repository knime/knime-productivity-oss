/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME AG, Zurich, Switzerland
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
 *   Created on 26 Nov 2021 by carlwitt
 */
package org.knime.workflowservices.knime.caller;

import java.awt.Component;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * Show a message and an indeterminate progress bar to inform the user about an ongoing operation.
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 */
@SuppressWarnings("serial")
class PanelLoading extends JPanel {

    private final JProgressBar m_loadingAnimation = new JProgressBar();

    private final JLabel m_loadingMessage = new JLabel();

    private final JPanel m_barAndLabel = new JPanel();

    private final Box m_panel = Box.createVerticalBox();

    PanelLoading(final String message) {
        m_barAndLabel.setLayout(new BoxLayout(m_barAndLabel, BoxLayout.PAGE_AXIS));

        // center horizontally
        m_loadingMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
        m_loadingMessage.setText(message);

        m_loadingAnimation.setIndeterminate(true);
        m_loadingAnimation.setAlignmentX(Component.CENTER_ALIGNMENT);

        m_barAndLabel.add(m_loadingAnimation);
        m_barAndLabel.add(m_loadingMessage);

        // center vertically
        m_panel.add(Box.createVerticalGlue());
        m_panel.add(m_barAndLabel);
        m_panel.add(Box.createVerticalGlue());
    }

    Component getPanel() {
        return m_panel;
    }

    void setMessage(final String message) {
        m_loadingMessage.setText(message);
    }

}