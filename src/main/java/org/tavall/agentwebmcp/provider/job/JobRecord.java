package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.tavall.agentwebmcp.operation.OperationExecution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record JobRecord(
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
        String failureReason,
        List<JobLogEntry> logs
) {
    public JobRecord {
        targetId = targetId == null || targetId.isBlank() ? "local" : targetId;
        serviceId = serviceId == null ? "" : serviceId;
        kind = kind == null ? JobKind.SERVICE_OPERATION : kind;
        operationId = operationId == null ? "" : operationId;
        prompt = prompt == null ? Optional.empty() : prompt;
        agentId = agentId == null ? Optional.empty() : agentId;
        repeatEverySeconds = repeatEverySeconds == null ? Optional.empty() : repeatEverySeconds;
        scheduledFor = scheduledFor == null ? createdAt : scheduledFor;
        nextRunAt = nextRunAt == null ? scheduledFor : nextRunAt;
        input = input == null || input.isNull() ? JsonNodeFactory.instance.objectNode() : input;
        output = output == null ? "" : output;
        failureReason = failureReason == null ? "" : failureReason;
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
