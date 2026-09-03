package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;

import java.util.Optional;

final class OperationTargets {
    private OperationTargets() {
    }

    static String resolve(OperationContext context, Optional<String> targetId) {
        String resolved = targetId.filter(value -> !value.isBlank()).orElse("local");
        context.targetProvider().inspectTarget(resolved);
        return resolved;
    }
}
