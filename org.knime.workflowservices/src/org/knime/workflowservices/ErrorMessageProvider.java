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
 *   Created on 20 Jan 2026 by Robin Gerling, KNIME GmbH, Konstanz, Germany
 */
package org.knime.workflowservices;

import java.util.Optional;
import java.util.function.Supplier;

import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.updates.StateProvider;
import org.knime.node.parameters.widget.message.TextMessage;
import org.knime.workflowservices.CallWorkflowParameters.WithError;

/**
 * Abstract base class for providers of error messages in workflow service nodes.
 *
 * @author Robin Gerling, KNIME GmbH, Konstanz, Germany
 * @since 5.10
 * @param <T> the type of the resource being loaded
 */
public abstract class ErrorMessageProvider<T> implements StateProvider<Optional<TextMessage.Message>> {

    private final Class<? extends StateProvider<WithError<T, Exception>>> m_resourcesOrErrorProviderClass;

    private final String m_errorMessageTitle;

    /**
     * Constructor.
     *
     * @param errorMessage the title of the displayed error message when an error occurs
     * @param resourcesOrErrorProviderClass the class of the provider that provides either the resource or an error
     */
    protected ErrorMessageProvider(final String errorMessage,
        final Class<? extends StateProvider<WithError<T, Exception>>> resourcesOrErrorProviderClass) {
        m_errorMessageTitle = errorMessage;
        m_resourcesOrErrorProviderClass = resourcesOrErrorProviderClass;
    }

    private Supplier<WithError<T, Exception>> m_resourcesOrErrorProvider;

    @Override
    public final void init(final StateProviderInitializer initializer) {
        m_resourcesOrErrorProvider = initializer.computeFromProvidedState(m_resourcesOrErrorProviderClass);
    }

    @Override
    public final Optional<TextMessage.Message> computeState(final NodeParametersInput parametersInput) {
        final var resourcesOrError = m_resourcesOrErrorProvider.get();
        if (resourcesOrError.hasError()) {
            final var exception = resourcesOrError.exception();
            return Optional.of(new TextMessage.Message(m_errorMessageTitle, toReadableString(exception),
                TextMessage.MessageType.ERROR));
        }
        return Optional.empty();
    }

    static String toReadableString(final Exception exception) {
        final var rootMessage = getRootMessage(exception);
        if (rootMessage.isEmpty() || exception instanceof IllegalStateException) {
            return rootMessage;
        }
        return exception.getClass().getSimpleName() + ": " + rootMessage;
    }

    static String getRootMessage(final Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        final var rootMessage = root.getMessage();
        return rootMessage == null ? "" : rootMessage;
    }
}
