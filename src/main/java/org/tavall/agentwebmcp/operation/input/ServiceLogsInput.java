package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record ServiceLogsInput(
        Optional<String> targetId,
        String serviceId,
        Optional<Integer> lines,
        Optional<String> cursor
) {
    public ServiceLogsInput {
        targetId = targetId == null ? Optional.empty() : targetId;
        lines = lines == null ? Optional.empty() : lines;
        cursor = cursor == null ? Optional.empty() : cursor;
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        lines.ifPresent(value -> {
            if (value < 1 || value > 1000) {
                throw new IllegalArgumentException("lines must be between 1 and 1000");
            }
        });
    }
}
