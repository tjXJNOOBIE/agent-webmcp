package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceListInput;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceState;
import org.tavall.agentwebmcp.provider.service.ServiceSummary;

import java.util.List;

public final class ServiceListOperation implements OperationHandler<ServiceListInput, List<ServiceSummary>> {
    @Override
    public List<ServiceSummary> execute(OperationContext context, ServiceListInput input) {
        OperationTargets.resolve(context, input.targetId());
        return context.managedServiceRepository().list().stream()
                .sorted()
                .map(serviceId -> inspectManagedService(context, serviceId))
                .toList();
    }

    private static ServiceSummary inspectManagedService(OperationContext context, String serviceId) {
        try {
            ServiceDetails details = context.serviceProvider().inspectService(serviceId);
            return new ServiceSummary(details.id(), details.description(), details.state(), details.subState());
        } catch (ProviderException exception) {
            if ("SERVICE_NOT_FOUND".equals(exception.code())) {
                return new ServiceSummary(serviceId, "Service unit not found", ServiceState.UNKNOWN, "not-found");
            }
            return new ServiceSummary(serviceId, "Service state unavailable", ServiceState.UNKNOWN, exception.code());
        }
    }
}
