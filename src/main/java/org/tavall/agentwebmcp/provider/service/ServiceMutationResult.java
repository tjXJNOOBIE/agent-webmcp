package org.tavall.agentwebmcp.provider.service;

public record ServiceMutationResult(String serviceId, String action, ServiceDetails observed) {
}
