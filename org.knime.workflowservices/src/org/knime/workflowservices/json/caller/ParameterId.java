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
 *   Created on May 31, 2018 by Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
package org.knime.workflowservices.json.caller;

import org.knime.productivity.callworkflow.table.ParameterId;

/**
 * Simple class holding both the fully qualified and simple id of a parameter.
 *
 * @author Tobias Urhaug, KNIME GmbH, Berlin, Germany
 */
class ParameterId implements Comparable<ParameterId> {

    private final String m_fullyQualifiedId;

    private final String m_simpleId;

    ParameterId(final String fullyQualifiedId, final String simpleId) {
        m_fullyQualifiedId = fullyQualifiedId;
        m_simpleId = simpleId;
    }

    String getId(final boolean useFullyQualifiedName) {
        return useFullyQualifiedName ? m_fullyQualifiedId : m_simpleId;
    }

    @Override
    public String toString() {
        return "Fully qualified Id: \"" + m_fullyQualifiedId + "\" Simple Id: \"" + m_simpleId + "\"";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final ParameterId other) {
        return m_fullyQualifiedId.compareTo(other.m_fullyQualifiedId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((m_fullyQualifiedId == null) ? 0 : m_fullyQualifiedId.hashCode());
        result = prime * result + ((m_simpleId == null) ? 0 : m_simpleId.hashCode());
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
        ParameterId other = (ParameterId)obj;
        return m_fullyQualifiedId.equals(other.m_fullyQualifiedId) && m_simpleId.equals(other.m_simpleId);
    }

}
