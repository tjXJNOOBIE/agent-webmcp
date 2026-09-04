package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationException;

final class ManagedServiceAccess {
    private ManagedServiceAccess() {
    }

    static void requireManaged(OperationContext context, String serviceId) {
        if (!context.managedServiceRepository().contains(serviceId)) {
            throw new OperationException(
                    "SERVICE_NOT_MANAGED",
                    "Service is not managed by Agent WebMCP: " + serviceId,
                    404
            );
        }
    }
}
