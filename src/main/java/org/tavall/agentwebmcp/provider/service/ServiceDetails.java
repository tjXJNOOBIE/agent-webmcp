package org.tavall.agentwebmcp.provider.service;

import java.util.Map;

public record ServiceDetails(
        String id,
        String description,
        ServiceState state,
        String subState,
        long pid,
        long memoryBytes,
        long cpuUsageNanoseconds,
        Map<String, String> providerMetadata
) {
}
