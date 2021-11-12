package org.knime.workflowservices.connection.util;

import java.util.Optional;
import java.util.concurrent.Callable;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.CheckUtils;

/**
 * Utility class for retrying a task with incrementally increasing backoff delay.
 */
public final class BackoffPolicy {

    /**
     * The default configuration to use for backoff delays in retries when polling for the workflow's job status.
     */
    public static final BackoffPolicy DEFAULT_BACKOFF_POLICY = new BackoffPolicy(1200, 1, 3);

    private final long m_base;

    private final long m_multiplier;

    private final int m_retries;

    /**
     * Construct a new policy describing how backoff delays are determined.
     *
     * @param base The initial value.
     * @param multiplier The multiplier applied to the previous delay duration.
     * @param retries The maximum number of retries.
     */
    public BackoffPolicy(final long base, final long multiplier, final int retries) {
        CheckUtils.checkArgument(retries >= 0, "Retries < 0: %d", retries);
        CheckUtils.checkArgument(multiplier >= 0, "Multiplier < 0: %d", multiplier);
        CheckUtils.checkArgument(retries >= 0, "Base < 0: %d", base);
        m_base = base;
        m_multiplier = multiplier;
        m_retries = retries;
    }

    /**
     * Returns the timeout value to apply after the <code>n</code>th retry.
     *
     * @param n
     * @return The timeout value in milliseconds.
     */
    public long getBackoffAt(final int n) {
        return (long)(m_base * Math.pow(m_multiplier, n));
    }

    public long getBase() {
        return m_base;
    }

    public long getMultiplier() {
        return m_multiplier;
    }

    public int getRetries() {
        return m_retries;
    }

    /**
     * Execute a given task returning some value. The task may throw an <code>ExecutionException</code> in which case
     * this procedure will retry running the task after a backoff delay increasing with the number of retries.
     *
     * @param <R> The return type of the task.
     * @param policy The backoff policy describing how delays are determined
     * @param task The task to run, should throw {@link HTTP5xxException} for server errors.
     * @return The result of the task.
     * @throws Exception
     */
    public static <R> R doWithBackoff(final BackoffPolicy policy, final Callable<R> task) throws Exception {
        HTTP5xxException lastExecException = null;
        for (int retry = 0; retry <= policy.getRetries(); retry++) {
            if (retry > 0 && lastExecException != null) { // NOSONAR: not always null
                Thread.sleep(policy.getBackoffAt(retry));
            }
            try {
                return task.call(); // return and do not retry
            } catch (HTTP5xxException e) { // NOSONAR: this kind of exception is handled
                lastExecException = e;
            }
        }
        throw (Exception)lastExecException.getCause(); // NOSONAR: can not be null
    }

    public static Optional<BackoffPolicy> loadFromSettings(final NodeSettingsRO settings) {
        try {
            NodeSettingsRO childSettings = settings.getNodeSettings("backoffPolicy");
            long backoffBase = childSettings.getLong("backoffBase");
            long backoffMultiplier = childSettings.getLong("backoffMultiplier");
            int backoffRetries = childSettings.getInt("backoffRetries");

            return Optional.of(new BackoffPolicy(backoffBase, backoffMultiplier, backoffRetries));
        } catch (InvalidSettingsException e) { // NOSONAR: exception handled properly, added in 4.3.1
            return Optional.empty();
        }
    }

    public void saveToSettings(final NodeSettingsWO settings) {
        NodeSettingsWO childSettings = settings.addNodeSettings("backoffPolicy");
        childSettings.addLong("backoffBase", getBase());
        childSettings.addLong("backoffMultiplier", getMultiplier());
        childSettings.addInt("backoffRetries", getRetries());
    }

    /**
     * Thrown by Callable passed to {@link BackoffPolicy#doWithBackoff(BackoffPolicy, Callable)} to indicate a
     * ServerError (see AP-15486 and <code>javax.ws.rs.core.Response.Status.Family.SERVER_ERROR</code>).
     */
    @SuppressWarnings("serial")
    public static final class HTTP5xxException extends Exception {

        public HTTP5xxException(final Throwable cause) {
            super(CheckUtils.checkArgumentNotNull(cause));
        }

    }
}
