package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record JobLogsInput(String jobId, Optional<Integer> lines, Optional<String> cursor) {
    public JobLogsInput {
        lines = lines == null ? Optional.empty() : lines;
        cursor = cursor == null ? Optional.empty() : cursor;
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        lines.ifPresent(value -> {
            if (value < 1 || value > 1000) {
                throw new IllegalArgumentException("lines must be between 1 and 1000");
            }
        });
    }
}
