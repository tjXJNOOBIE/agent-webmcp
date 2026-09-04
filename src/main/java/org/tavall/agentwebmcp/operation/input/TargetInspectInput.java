package org.tavall.agentwebmcp.operation.input;

public record TargetInspectInput(String targetId) {
    public TargetInspectInput {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId is required");
        }
    }
}
