package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.agentwebmcp.operation.OperationExecution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record JobRecord(
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
        String failureReason,
        List<JobLogEntry> logs
) {
    public JobRecord {
        agentId = agentId == null ? Optional.empty() : agentId;
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
