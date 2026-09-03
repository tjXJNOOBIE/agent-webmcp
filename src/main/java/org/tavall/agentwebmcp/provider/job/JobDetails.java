package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.agentwebmcp.operation.OperationExecution;

import java.time.Instant;

public record JobDetails(
        String id,
        String operationId,
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
