package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceLogsInput;
import org.tavall.agentwebmcp.provider.service.ServiceLogSlice;

public final class ServiceLogsOperation implements OperationHandler<ServiceLogsInput, ServiceLogSlice> {
    @Override
    public ServiceLogSlice execute(OperationContext context, ServiceLogsInput input) {
        OperationTargets.resolve(context, input.targetId());
        ManagedServiceAccess.requireManaged(context, input.serviceId());
        return context.serviceProvider().readLogs(input.serviceId(), input.lines().orElse(200), input.cursor());
    }
}
