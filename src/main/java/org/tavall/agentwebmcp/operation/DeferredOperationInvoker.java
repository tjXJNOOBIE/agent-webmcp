package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class DeferredOperationInvoker implements OperationInvoker {
    private final AtomicReference<OperationInvoker> delegate = new AtomicReference<>();

    public void bind(OperationInvoker operationInvoker) {
        Objects.requireNonNull(operationInvoker, "operationInvoker");
        if (!delegate.compareAndSet(null, operationInvoker)) {
            throw new IllegalStateException("Operation invoker is already bound");
        }
    }

    @Override
    public OperationExecution execute(String operationId, JsonNode input) {
        OperationInvoker bound = delegate.get();
        if (bound == null) {
            throw new IllegalStateException("Operation invoker is not bound");
        }
        return bound.execute(operationId, input);
    }
}
