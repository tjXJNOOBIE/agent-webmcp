package org.tavall.agentwebmcp.provider.job;

import java.time.Instant;
import java.util.Optional;

public record JobSummary(
        String id,
        String serviceId,
        JobKind kind,
        String operationId,
        Optional<String> agentId,
        JobState state,
        Instant createdAt,
        Instant nextRunAt,
        Instant completedAt,
        Optional<Long> repeatEverySeconds
) { }
