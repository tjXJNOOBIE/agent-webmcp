package org.tavall.agentwebmcp.provider.job;

import java.time.Instant;

public record JobSummary(
        String id,
        String operationId,
        JobState state,
        Instant createdAt,
        Instant completedAt
) {
}
