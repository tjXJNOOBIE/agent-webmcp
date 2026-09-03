package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;

public final class ServiceInspectOperation implements OperationHandler<ServiceIdInput, ServiceDetails> {
    @Override
    public ServiceDetails execute(OperationContext context, ServiceIdInput input) {
        OperationTargets.resolve(context, input.targetId());
        return context.serviceProvider().inspectService(input.serviceId());
    }
}
