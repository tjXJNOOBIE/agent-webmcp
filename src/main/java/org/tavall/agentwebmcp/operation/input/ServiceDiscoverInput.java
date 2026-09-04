package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record ServiceDiscoverInput(Optional<String> targetId, boolean includeAi) {
    public ServiceDiscoverInput {
        targetId = targetId == null ? Optional.empty() : targetId.map(String::trim).filter(value -> !value.isEmpty());
    }
}
