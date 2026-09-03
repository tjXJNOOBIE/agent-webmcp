package org.tavall.agentwebmcp.provider.metrics;

public interface MetricsProvider {
    String providerName();

    SystemMetricsSnapshot snapshot();
}
