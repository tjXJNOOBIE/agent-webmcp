package org.tavall.agentwebmcp.provider.process;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCommandExecutorTest {
    @Test
    void forciblyTerminatesProviderProcessAtTimeout() {
        ProcessCommandExecutor executor = new ProcessCommandExecutor();
        Instant started = Instant.now();

        CommandResult result = executor.execute(List.of("sleep", "10"), Duration.ofMillis(100));

        assertTrue(result.timedOut());
        assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
    }
}
