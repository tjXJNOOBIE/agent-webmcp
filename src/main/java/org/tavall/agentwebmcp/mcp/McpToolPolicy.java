package org.tavall.agentwebmcp.mcp;

import org.tavall.agentwebmcp.operation.OperationRegistration;

import java.util.Set;

public final class McpToolPolicy {
    private static final Set<String> EXPOSED_OPERATION_IDS = Set.of(
            "system.status",
            "metrics.snapshot",
            "service.list",
            "service.inspect",
            "service.status",
            "service.logs",
            "service.start",
            "service.stop",
            "service.restart",
            "service.reload"
    );

    private McpToolPolicy() {
    }

    public static boolean allows(String operationId) {
        return EXPOSED_OPERATION_IDS.contains(operationId);
    }

    public static boolean allows(OperationRegistration<?, ?> registration) {
        return allows(registration.descriptor().id().value());
    }

    public static int exposedToolCount() {
        return EXPOSED_OPERATION_IDS.size();
    }
}
