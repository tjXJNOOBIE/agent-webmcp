package org.tavall.agentwebmcp.operation;

import java.util.Objects;

public record OperationRegistration<I, R>(
        OperationDescriptor descriptor,
        Class<I> inputType,
        OperationHandler<I, R> handler
) {
    public OperationRegistration {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(handler, "handler");
    }
}
