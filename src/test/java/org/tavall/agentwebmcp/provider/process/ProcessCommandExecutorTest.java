package org.tavall.agentwebmcp.provider.process;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.provider.ProviderException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCommandExecutorTest {
    private final ProcessCommandExecutor executor = new ProcessCommandExecutor();

    @Test
    void writesStdinAndCapturesStdoutAndStderrSeparately() {
        CommandResult result = executor.execute(
                List.of("sh", "-c", "read value; printf 'out:%s' \"$value\"; printf 'err:%s' \"$value\" >&2"),
                Duration.ofSeconds(2), "hello\n");
        assertTrue(result.successful());
        assertEquals("out:hello", result.stdout());
        assertEquals("err:hello", result.stderr());
    }

    @Test
    void boundsStdinBeforeProcessExecution() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(
                List.of("cat"), Duration.ofSeconds(2), "x".repeat(1_048_577)));
    }

    @Test
    void blockedStdinDeliveryCannotEscapeExecutionDeadline() {
        Instant started = Instant.now();
        CommandResult result = executor.execute(
                List.of("sh", "-c", "sleep 10"),
                Duration.ofMillis(100),
                "x".repeat(1_048_576));
        assertTrue(result.timedOut());
        assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
    }

    @Test
    void boundsProcessOutput() {
        ProviderException failure = assertThrows(ProviderException.class, () -> executor.execute(
                List.of("sh", "-c", "head -c 1048577 /dev/zero"), Duration.ofSeconds(3)));
        assertEquals("PROCESS_OUTPUT_TOO_LARGE", failure.code());
    }

    @Test
    void boundsStdinWriteInsideTheSameProcessDeadline() {
        Instant started = Instant.now();
        CommandResult result = executor.execute(
                List.of("sh", "-c", "sleep 10"),
                Duration.ofMillis(150),
                "x".repeat(1_048_576));
        assertTrue(result.timedOut());
        assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
    }

    @Test
    void forciblyTerminatesProviderProcessAtTimeout() {
        Instant started = Instant.now();
        CommandResult result = executor.execute(List.of("sleep", "10"), Duration.ofMillis(100));
        assertTrue(result.timedOut());
        assertTrue(Duration.between(started, Instant.now()).compareTo(Duration.ofSeconds(3)) < 0);
    }
}
