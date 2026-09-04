package org.tavall.agentwebmcp.provider.metrics;

import java.time.Instant;

public record SystemMetricsSnapshot(
        Instant capturedAt,
        int availableProcessors,
        double systemLoadAverage,
        double systemCpuLoad,
        double processCpuLoad,
        long totalPhysicalMemoryBytes,
        long freePhysicalMemoryBytes,
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long uptimeMillis
) {
}
