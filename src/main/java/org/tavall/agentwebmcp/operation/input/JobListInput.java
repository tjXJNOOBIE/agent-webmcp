package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record JobListInput(Optional<Integer> limit) {
    public JobListInput {
        limit = limit == null ? Optional.empty() : limit;
        limit.ifPresent(value -> {
            if (value < 1 || value > 1000) {
                throw new IllegalArgumentException("limit must be between 1 and 1000");
            }
        });
    }
}
