package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record JobRequest(
        String targetId,
        String serviceId,
        JobKind kind,
        String operationId,
        JsonNode input,
        Optional<String> prompt,
        Optional<Instant> runAt,
        Optional<Long> repeatEverySeconds,
        int timeoutSeconds,
        Optional<String> agentId
) {
    public JobRequest {
        targetId = targetId == null || targetId.isBlank() ? "local" : targetId.trim();
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        serviceId = serviceId.trim();
        kind = Objects.requireNonNull(kind, "kind");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId is required");
        }
        operationId = operationId.trim();
        input = input == null || input.isNull() ? JsonNodeFactory.instance.objectNode() : input;
        if (!input.isObject()) {
            throw new IllegalArgumentException("input must be an object");
        }
        prompt = prompt == null ? Optional.empty() : prompt.map(String::trim).filter(value -> !value.isEmpty());
        runAt = runAt == null ? Optional.empty() : runAt;
        repeatEverySeconds = repeatEverySeconds == null ? Optional.empty() : repeatEverySeconds;
        agentId = agentId == null ? Optional.empty() : agentId.map(String::trim).filter(value -> !value.isEmpty());
        if (timeoutSeconds < 1 || timeoutSeconds > 3600) {
            throw new IllegalArgumentException("timeoutSeconds must be between 1 and 3600");
        }
        if (kind == JobKind.CODEX_PROMPT && prompt.isEmpty()) {
            throw new IllegalArgumentException("Codex jobs require a prompt");
        }
        if (kind == JobKind.SERVICE_OPERATION && prompt.isPresent()) {
            throw new IllegalArgumentException("Deterministic service jobs cannot contain a prompt");
        }
        if (kind == JobKind.CODEX_PROMPT && repeatEverySeconds.isPresent()) {
            throw new IllegalArgumentException("Recurring Codex jobs are not supported");
        }
    }
}
