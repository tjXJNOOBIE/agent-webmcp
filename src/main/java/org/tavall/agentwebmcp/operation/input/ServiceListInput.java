package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record ServiceListInput(Optional<String> targetId) {
    public ServiceListInput {
        targetId = targetId == null ? Optional.empty() : targetId;
    }
}
