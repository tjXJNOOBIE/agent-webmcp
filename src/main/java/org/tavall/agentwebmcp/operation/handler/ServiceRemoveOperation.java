package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceManagementResult;

public final class ServiceRemoveOperation implements OperationHandler<ServiceIdInput, ServiceManagementResult> {
    @Override
    public ServiceManagementResult execute(OperationContext context, ServiceIdInput input) {
        OperationTargets.resolve(context, input.targetId());
        if (!context.managedServiceRepository().contains(input.serviceId())) {
            return new ServiceManagementResult(input.serviceId(), "remove", false, null);
        }

        ServiceDetails observed = null;
        try {
            observed = context.serviceProvider().inspectService(input.serviceId());
        } catch (ProviderException ignored) {
            // Removing Agent WebMCP enrollment is independent from provider availability.
        }
        boolean changed = context.managedServiceRepository().remove(input.serviceId());
        return new ServiceManagementResult(input.serviceId(), "remove", changed, observed);
    }
}
