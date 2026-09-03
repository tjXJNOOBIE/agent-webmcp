package org.tavall.agentwebmcp.web;

import org.tavall.agentwebmcp.operation.OperationRegistration;

import java.util.Set;

/** Browser-native WebMCP projection policy over the canonical operation catalog. */
public final class WebMcpToolPolicy {
    private static final Set<String> EXPOSED_OPERATION_IDS = Set.of(
            "system.status",
            "metrics.snapshot",
            "service.list",
            "service.add",
            "service.remove",
            "service.inspect",
            "service.status",
            "service.logs",
            "service.diagnostics",
            "service.start",
            "service.stop",
            "service.restart",
            "service.reload",
            "job.list",
            "job.inspect",
            "job.logs"
    );

    private WebMcpToolPolicy() {
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
