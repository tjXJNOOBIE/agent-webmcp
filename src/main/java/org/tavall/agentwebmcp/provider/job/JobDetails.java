package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.agentwebmcp.operation.OperationExecution;

import java.time.Instant;
import java.util.Optional;

public record JobDetails(
        String id,
        String operationId,
        Optional<String> agentId,
        JobState state,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        int timeoutSeconds,
        JsonNode input,
        OperationExecution execution,
        String failureReason
) {
}
