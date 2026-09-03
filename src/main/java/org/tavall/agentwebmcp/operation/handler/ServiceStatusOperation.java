package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceState;

public final class ServiceStatusOperation implements OperationHandler<ServiceIdInput, ServiceStatusOperation.Result> {
    @Override
    public Result execute(OperationContext context, ServiceIdInput input) {
        String targetId = OperationTargets.resolve(context, input.targetId());
        ServiceDetails details = context.serviceProvider().inspectService(input.serviceId());
        return new Result(targetId, details.id(), details.state(), details.subState(), details.pid());
    }

    public record Result(String targetId, String serviceId, ServiceState state, String subState, long pid) {
    }
}
