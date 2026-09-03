package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceListInput;
import org.tavall.agentwebmcp.provider.service.ServiceSummary;

import java.util.List;

public final class ServiceListOperation implements OperationHandler<ServiceListInput, List<ServiceSummary>> {
    @Override
    public List<ServiceSummary> execute(OperationContext context, ServiceListInput input) {
        OperationTargets.resolve(context, input.targetId());
        return context.serviceProvider().listServices();
    }
}
