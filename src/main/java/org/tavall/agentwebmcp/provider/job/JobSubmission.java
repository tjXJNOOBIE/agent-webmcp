package org.tavall.agentwebmcp.provider.job;

import java.time.Instant;
import java.util.Optional;

public record JobSubmission(
        String jobId,
        String serviceId,
        JobKind kind,
        String operationId,
        Optional<String> agentId,
        JobState state,
        Instant nextRunAt,
        Optional<Long> repeatEverySeconds,
        int timeoutSeconds
) { }
