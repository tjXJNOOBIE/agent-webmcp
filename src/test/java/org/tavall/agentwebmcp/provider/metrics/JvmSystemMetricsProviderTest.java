package org.tavall.agentwebmcp.provider.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmSystemMetricsProviderTest {
    @Test
    void readsBoundedJvmAndOperatingSystemSnapshotWithoutShellingOut() {
        SystemMetricsSnapshot snapshot = new JvmSystemMetricsProvider().snapshot();

        assertNotNull(snapshot.capturedAt());
        assertTrue(snapshot.availableProcessors() >= 1);
        assertTrue(snapshot.heapUsedBytes() >= 0);
        assertTrue(snapshot.uptimeMillis() >= 0);
    }
}
