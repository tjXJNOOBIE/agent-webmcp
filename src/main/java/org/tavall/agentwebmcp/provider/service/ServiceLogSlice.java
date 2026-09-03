package org.tavall.agentwebmcp.provider.service;

public record ServiceLogSlice(String serviceId, String output, String cursor, int requestedLines) {
}
