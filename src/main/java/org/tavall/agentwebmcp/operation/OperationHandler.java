package org.tavall.agentwebmcp.operation;

@FunctionalInterface
public interface OperationHandler<I, R> {
    R execute(OperationContext context, I input) throws Exception;
}
