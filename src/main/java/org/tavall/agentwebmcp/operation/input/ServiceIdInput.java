package org.tavall.agentwebmcp.operation.input;

import org.tavall.agentwebmcp.service.ServiceIdSyntax;

import java.util.Optional;

public record ServiceIdInput(Optional<String> targetId, String serviceId) {
    public ServiceIdInput {
        targetId = targetId == null ? Optional.empty() : targetId;
        serviceId = ServiceIdSyntax.require(serviceId);
    }
}
