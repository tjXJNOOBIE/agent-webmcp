package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.agentwebmcp.operation.OperationExecution;

import java.time.Instant;
import java.util.Optional;

public record JobDetails(
        String id,
        String targetId,
        String serviceId,
        JobKind kind,
        String operationId,
        Optional<String> prompt,
        Optional<String> agentId,
        JobState state,
        Instant createdAt,
        Instant scheduledFor,
        Instant nextRunAt,
        Instant startedAt,
        Instant completedAt,
        Optional<Long> repeatEverySeconds,
        int timeoutSeconds,
        JsonNode input,
        OperationExecution execution,
        String output,
        String failureReason
) { }
