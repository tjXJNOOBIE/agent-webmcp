package org.tavall.agentwebmcp.provider.service;

public record ServiceSummary(String id, String description, ServiceState state, String subState) {
}
