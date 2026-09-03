package org.tavall.agentwebmcp.provider.process;

import org.tavall.agentwebmcp.provider.ProviderException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProcessCommandExecutor implements CommandExecutor {
    private static final Duration OUTPUT_DRAIN_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(2);

    @Override
    public CommandResult execute(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        Process process = null;
        ExecutorService streamReaders = null;
        try {
            Process startedProcess = new ProcessBuilder(command).start();
            process = startedProcess;
            streamReaders = Executors.newVirtualThreadPerTaskExecutor();
            Future<byte[]> stdout = streamReaders.submit(() -> processInput(startedProcess));
            Future<byte[]> stderr = streamReaders.submit(() -> processError(startedProcess));

            boolean exited = startedProcess.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                terminate(startedProcess);
                return new CommandResult(-1, decodeBestEffort(stdout), decodeBestEffort(stderr), true);
            }
            return new CommandResult(startedProcess.exitValue(), decodeRequired(stdout), decodeRequired(stderr), false);
        } catch (IOException exception) {
            terminate(process);
            throw new ProviderException("PROCESS_START_FAILED", "Unable to start provider command: " + command.getFirst(), 503);
        } catch (InterruptedException exception) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw new ProviderException("PROCESS_INTERRUPTED", "Provider command was interrupted", 500);
        } finally {
            if (streamReaders != null) {
                streamReaders.shutdownNow();
            }
            closeProcessStreams(process);
        }
    }

    private static byte[] processInput(Process process) throws IOException {
        return process.getInputStream().readAllBytes();
    }

    private static byte[] processError(Process process) throws IOException {
        return process.getErrorStream().readAllBytes();
    }

    private static String decodeRequired(Future<byte[]> bytes) {
        try {
            return new String(bytes.get(OUTPUT_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderException("PROCESS_INTERRUPTED", "Provider output read was interrupted", 500);
        } catch (ExecutionException | TimeoutException | CancellationException exception) {
            bytes.cancel(true);
            throw new ProviderException("PROCESS_OUTPUT_FAILED", "Unable to read provider command output", 500);
        }
    }

    private static String decodeBestEffort(Future<byte[]> bytes) {
        try {
            return new String(bytes.get(OUTPUT_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException | CancellationException exception) {
            bytes.cancel(true);
            return "";
        }
    }

    private static void terminate(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroyForcibly();
        try {
            process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
        }
    }
}
