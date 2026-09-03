package org.tavall.agentwebmcp.operation.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Optional;

public record JobExecuteInput(
        String operationId,
        JsonNode input,
        Optional<Integer> timeoutSeconds,
        Optional<String> agentId
) {
    public JobExecuteInput {
        timeoutSeconds = timeoutSeconds == null ? Optional.empty() : timeoutSeconds;
        agentId = agentId == null ? Optional.empty() : agentId.map(String::trim).filter(value -> !value.isEmpty());
        input = input == null || input.isNull() ? JsonNodeFactory.instance.objectNode() : input;
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId is required");
        }
        timeoutSeconds.ifPresent(value -> {
            if (value < 1 || value > 900) {
                throw new IllegalArgumentException("timeoutSeconds must be between 1 and 900");
            }
        });
        agentId.ifPresent(value -> {
            if (value.length() > 128 || !value.matches("[A-Za-z0-9_.:@/-]+")) {
                throw new IllegalArgumentException("agentId contains unsupported characters");
            }
        });
    }
}
