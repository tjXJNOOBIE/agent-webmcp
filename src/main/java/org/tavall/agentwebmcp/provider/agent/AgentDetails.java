package org.tavall.agentwebmcp.provider.agent;

import java.time.Instant;
import java.util.List;

public record AgentDetails(
        String id,
        String displayName,
        String state,
        String targetId,
        String runtimeKind,
        String runtimeVersion,
        Instant lastHeartbeatAt,
        List<String> capabilities
) {
    public AgentDetails {
        capabilities = List.copyOf(capabilities);
    }
}
