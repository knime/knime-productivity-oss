/* ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright by KNIME.com, Zurich, Switzerland
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
 *   Created on Feb 17, 2015 by wiswedel
 */
package com.knime.explorer.nodes.callworkflow;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;

import com.knime.enterprise.server.rest.api.Util;
import com.knime.enterprise.server.rest.api.v4.jobs.Job;
import com.knime.enterprise.server.rest.api.v4.jobs.ent.WorkflowJob;
import com.knime.enterprise.server.rest.api.v4.repository.Jobs;

/**
 * A remote workflow representation. Workflows are kept in a cache and re-used with exclusive locks.
 * @author Bernd Wiswedel, KNIME.com, Zurich, Switzerland
 */
final class RemoteWorkflowBackend implements IWorkflowBackend, AutoCloseable {

    private final UUID m_uuid;
    private final Job m_jobEndpoint;

    private RemoteWorkflowBackend(final UUID uuid, final Job m) {
        m_uuid = uuid;
        m_jobEndpoint = m;
    }

    static RemoteWorkflowBackend newInstance(final Lookup lookup) throws Exception {
        Jobs jobs = JAXRSClientFactory.create(lookup.m_hostAndPort,
            Jobs.class, Util.getJaxRSProviders(), lookup.m_username, lookup.m_password,
            null);
        Response res = jobs.createNewJobAsMason(lookup.m_workflow);
        WorkflowJob job = res.readEntity(WorkflowJob.class);


        Job jobEnpoint = JAXRSClientFactory.create(lookup.m_hostAndPort,
            Job.class, Util.getJaxRSProviders(), lookup.m_username, lookup.m_password,
            null);

        return new RemoteWorkflowBackend(job.getId(), jobEnpoint);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonObject> getInputNodes() {
        try {
            Response res = m_jobEndpoint.getJobAsMason(m_uuid);
            WorkflowJob job = res.readEntity(WorkflowJob.class);

            return job.getInputParameters();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setInputNodes(final Map<String, JsonObject> input) {
        try {
            Response res = m_jobEndpoint.getJobAsMason(m_uuid);
            WorkflowJob job = res.readEntity(WorkflowJob.class);
            job = WorkflowJob.builder(job).setInputParameters(input).build();

            m_jobEndpoint.modifyJob(m_uuid, job);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, JsonObject> getOutputNodes() {
        try {
            Response res = m_jobEndpoint.getJobAsMason(m_uuid);
            WorkflowJob job = res.readEntity(WorkflowJob.class);
            return job.getOutputParameters();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowState execute() {
        com.knime.enterprise.utility.WorkflowState syncExec;
        try {
            Response res = m_jobEndpoint.executeJob(m_uuid, false, Collections.EMPTY_MAP);
            syncExec = res.readEntity(WorkflowJob.class).getState();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        switch (syncExec) {
            case EXECUTED:
                return WorkflowState.EXECUTED;
            case EXECUTING:
                return WorkflowState.RUNNING;
            default:
                return WorkflowState.IDLE;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getWorkflowMessage() {
        // TODO
        return "";
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws Exception {
        try {
            m_jobEndpoint.deleteJob(m_uuid);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final class Lookup {
        private final String m_hostAndPort;
        private final String m_workflow;
        private final String m_username;
        private final String m_password;

        private Lookup(final String hostAndPort, final String workflow, final String username, final String password) {
            m_hostAndPort = hostAndPort;
            m_workflow = workflow;
            m_username = username;
            m_password = password;
        }

        static Lookup newLookup(final String hostAndPort, final String workflow,
            final String username, final String password) {
            return new Lookup(hostAndPort, workflow, username, password);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this, true);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object obj) {
            return EqualsBuilder.reflectionEquals(this, obj, true);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }
}
