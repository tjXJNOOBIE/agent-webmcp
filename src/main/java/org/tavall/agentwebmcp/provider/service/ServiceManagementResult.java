package org.tavall.agentwebmcp.provider.service;

public record ServiceManagementResult(
        String serviceId,
        String action,
        boolean changed,
        ServiceDetails observedService
) {
}
