package org.tavall.agentwebmcp.operation;

import java.util.Objects;

public record OperationDescriptor(
        OperationId id,
        String description,
        OperationAccess access
) {
    public OperationDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(access, "access");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
    }
}
