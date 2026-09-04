package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record OperationExecution(
        String operationId,
        OperationExecutionStatus status,
        Instant startedAt,
        Instant completedAt,
        long durationMillis,
        JsonNode output,
        OperationError error
) {
}
