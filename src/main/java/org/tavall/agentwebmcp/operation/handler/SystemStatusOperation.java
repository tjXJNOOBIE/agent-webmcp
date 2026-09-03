package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.EmptyInput;

public final class SystemStatusOperation implements OperationHandler<EmptyInput, SystemStatusOperation.Result> {
    @Override
    public Result execute(OperationContext context, EmptyInput input) {
        return new Result(
                context.runtimeVersion(),
                context.authMode().name(),
                context.operationCount(),
                context.targetProvider().providerName(),
                context.serviceProvider().providerName(),
                context.serviceProvider().available(),
                context.metricsProvider().providerName(),
                context.jobProvider().providerName(),
                System.getProperty("java.version")
        );
    }

    public record Result(
            String version,
            String authMode,
            int operationCount,
            String targetProvider,
            String serviceProvider,
            boolean serviceProviderAvailable,
            String metricsProvider,
            String jobProvider,
            String javaVersion
    ) {
    }
}
