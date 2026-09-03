package org.tavall.agentwebmcp.provider.job;

import java.time.Instant;
import java.util.Optional;

public record JobSummary(
        String id,
        String operationId,
        Optional<String> agentId,
        JobState state,
        Instant createdAt,
        Instant completedAt
) {
}
