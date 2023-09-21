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
 *   Created on 13 Sept 2023 by carlwitt
 */
package org.knime.workflowservices;

import java.beans.PropertyChangeListener;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.knime.core.node.NodeLogger;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.workflowservices.connection.CallWorkflowConnectionConfiguration;

/**
 * Generic asynchronous execution of operations on a {@link CallWorkflowConnectionConfiguration}.
 *
 * @param <R> data that is being fetched
 *
 * @author Carl Witt, KNIME AG, Zurich, Switzerland
 * @noreference non-public API
 */
public final class Fetcher<R> extends SwingWorkerWithContext<R, Void> {

    /**
     * @param <R> output of the operation on the connection
     * @noreference non-public API
     */
    public interface ConnectionCallable<R> {
        /**
         * @param configuration to asynchronously perform an operation on
         * @return the operation's result, e.g., available versions for a callee
         * @throws Exception
         */
        R call(CallWorkflowConnectionConfiguration configuration) throws Exception;
    }

    /**
     * A control that displays async fetched data.
     *
     * @param <R> data to display
     */
    public interface StatefulConsumer<R> extends Consumer<R> {
        /**
         * Disable the control and clear fetched data.
         */
        void clear();

        /**
         * Indicate that the data is now being fetched.
         */
        void loading();

        /**
         * Display the fetched data.
         *
         * This is called on the event dispatch thread, so updating the UI is safe but no heavy work should be done
         * here.
         */
        @Override
        void accept(R result);

        /**
         * Called instead of {@link #accept(Object)} if an error occurs during fetching the data. May or may not disable
         * the control.
         *
         * @param cause error message to display
         */
        void exception(String cause);
    }

    /**
     * A control that displays async fetched data and allows to make a selection.
     *
     * @param <R> data to display
     * @param <M> output of the selection operation
     *
     * @noreference non-public API
     */
    public interface Processor<R, M> extends StatefulConsumer<R>, Supplier<M> {
        /**
         * @return the current selected value.
         */
        @Override
        M get();

        /**
         * @param listener to call when the selection changes
         */
        void addListener(PropertyChangeListener listener);

        /**
         * A setter to apply persisted (via node settings) or injected (via flow variable) state.
         *
         * @param value
         */
        void set(M value);
    }

    private final StatefulConsumer<R> m_control;

    private final ConnectionCallable<R> m_callable;

    private final CallWorkflowConnectionConfiguration m_configuration;

    private final Consumer<R> m_dataHandler;

    Fetcher(final CallWorkflowConnectionConfiguration configuration, final StatefulConsumer<R> control,
        final ConnectionCallable<R> callable) {
        this(configuration, control, r -> {
        }, callable);
    }

    Fetcher(final CallWorkflowConnectionConfiguration configuration, final StatefulConsumer<R> control,
        final Consumer<R> dataHandler, final ConnectionCallable<R> callable) {
        m_configuration = configuration;
        m_control = control;
        m_callable = callable;
        m_dataHandler = dataHandler;
    }

    @Override
    protected R doInBackgroundWithContext() throws Exception {
        m_control.loading();
        return m_callable.call(m_configuration);
    }

    @Override
    protected void doneWithContext() {
        if (!isCancelled()) {
            try {
                final var result = get();
                m_control.accept(result);
                m_dataHandler.accept(result);
            } catch (InterruptedException | CancellationException e) {
                NodeLogger.getLogger(this.getClass()).debug(e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                NodeLogger.getLogger(this.getClass()).debug(e);
                m_control.exception(e.getMessage());
            }
        }
    }

}
