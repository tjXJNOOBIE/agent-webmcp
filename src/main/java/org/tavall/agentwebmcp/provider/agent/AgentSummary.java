package org.tavall.agentwebmcp.provider.agent;

import java.time.Instant;

public record AgentSummary(
        String id,
        String displayName,
        String state,
        String targetId,
        String runtimeVersion,
        Instant lastHeartbeatAt
) { }
