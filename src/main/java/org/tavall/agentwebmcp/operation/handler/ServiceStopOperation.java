package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.service.ServiceMutationResult;

public final class ServiceStopOperation implements OperationHandler<ServiceIdInput, ServiceMutationResult> {
    @Override
    public ServiceMutationResult execute(OperationContext context, ServiceIdInput input) {
        OperationTargets.resolve(context, input.targetId());
        ManagedServiceAccess.requireManaged(context, input.serviceId());
        return context.serviceProvider().stopService(input.serviceId());
    }
}
