package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceManagementResult;

public final class ServiceAddOperation implements OperationHandler<ServiceIdInput, ServiceManagementResult> {
    @Override
    public ServiceManagementResult execute(OperationContext context, ServiceIdInput input) {
        OperationTargets.resolve(context, input.targetId());
        ServiceDetails details = context.serviceProvider().inspectService(input.serviceId());
        boolean changed = context.managedServiceRepository().add(details.id());
        return new ServiceManagementResult(details.id(), "add", changed, details);
    }
}
