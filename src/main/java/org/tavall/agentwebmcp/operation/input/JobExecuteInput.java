package org.tavall.agentwebmcp.operation.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

public record JobExecuteInput(
        Optional<String> targetId,
        String serviceId,
        Optional<String> operationId,
        JsonNode input,
        Optional<String> prompt,
        Optional<Instant> runAt,
        Optional<Long> repeatEverySeconds,
        Optional<Integer> timeoutSeconds,
        Optional<String> agentId
) {
    private static final Pattern SERVICE_ID = Pattern.compile("[A-Za-z0-9_.@:-]+");

    public JobExecuteInput {
        targetId = normalize(targetId);
        if (serviceId == null || serviceId.isBlank() || !SERVICE_ID.matcher(serviceId.trim()).matches()) {
            throw new IllegalArgumentException("serviceId is required and contains unsupported characters");
        }
        serviceId = serviceId.trim();
        operationId = normalize(operationId);
        prompt = normalize(prompt);
        agentId = normalize(agentId);
        runAt = runAt == null ? Optional.empty() : runAt;
        repeatEverySeconds = repeatEverySeconds == null ? Optional.empty() : repeatEverySeconds;
        timeoutSeconds = timeoutSeconds == null ? Optional.empty() : timeoutSeconds;
        input = input == null || input.isNull() ? JsonNodeFactory.instance.objectNode() : input;
        if (!input.isObject()) {
            throw new IllegalArgumentException("input must be a JSON object");
        }
        if (prompt.isPresent() == operationId.isPresent()) {
            throw new IllegalArgumentException("Provide either prompt or operationId, but not both");
        }
        if (prompt.filter(value -> value.length() > 100_000).isPresent()) {
            throw new IllegalArgumentException("prompt exceeds 100000 characters");
        }
        if (repeatEverySeconds.isPresent() && prompt.isPresent()) {
            throw new IllegalArgumentException("Recurring AI prompt jobs are not supported");
        }
        repeatEverySeconds.ifPresent(seconds -> {
            if (seconds < 60 || seconds > 31_536_000) {
                throw new IllegalArgumentException("repeatEverySeconds must be between 60 and 31536000");
            }
        });
        timeoutSeconds.ifPresent(seconds -> {
            if (seconds < 1 || seconds > 3600) {
                throw new IllegalArgumentException("timeoutSeconds must be between 1 and 3600");
            }
        });
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value == null ? Optional.empty() : value.map(String::trim).filter(text -> !text.isEmpty());
    }
}
