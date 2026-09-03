package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record ServiceIdInput(Optional<String> targetId, String serviceId) {
    public ServiceIdInput {
        targetId = targetId == null ? Optional.empty() : targetId;
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
    }
}
