package org.tavall.agentwebmcp.operation.input;

public record AgentInspectInput(String agentId) {
    public AgentInspectInput {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        agentId = agentId.trim();
    }
}
