package org.tavall.agentwebmcp.provider.metrics;

import org.tavall.dependency.annotations.DelegatesTo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;

@DelegatesTo(MetricsProvider.class)
public final class JvmSystemMetricsProvider implements MetricsProvider {
    @Override
    public String providerName() {
        return "jvm-os-mxbean";
    }

    @Override
    public SystemMetricsSnapshot snapshot() {
        java.lang.management.OperatingSystemMXBean baseOperatingSystem = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        double systemCpuLoad = -1.0;
        double processCpuLoad = -1.0;
        long totalPhysicalMemoryBytes = -1L;
        long freePhysicalMemoryBytes = -1L;
        if (baseOperatingSystem instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            systemCpuLoad = finiteOrUnavailable(operatingSystem.getCpuLoad());
            processCpuLoad = finiteOrUnavailable(operatingSystem.getProcessCpuLoad());
            totalPhysicalMemoryBytes = operatingSystem.getTotalMemorySize();
            freePhysicalMemoryBytes = operatingSystem.getFreeMemorySize();
        }

        return new SystemMetricsSnapshot(
                Instant.now(),
                baseOperatingSystem.getAvailableProcessors(),
                finiteOrUnavailable(baseOperatingSystem.getSystemLoadAverage()),
                systemCpuLoad,
                processCpuLoad,
                totalPhysicalMemoryBytes,
                freePhysicalMemoryBytes,
                heap.getUsed(),
                heap.getMax(),
                nonHeap.getUsed(),
                ManagementFactory.getRuntimeMXBean().getUptime()
        );
    }

    private static double finiteOrUnavailable(double value) {
        return Double.isFinite(value) ? value : -1.0;
    }
}
