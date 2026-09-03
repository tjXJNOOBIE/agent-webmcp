package org.tavall.agentwebmcp.operation.input;

import java.util.Optional;

public record MetricsSnapshotInput(Optional<String> targetId) {
    public MetricsSnapshotInput {
        targetId = targetId == null ? Optional.empty() : targetId;
    }
}
