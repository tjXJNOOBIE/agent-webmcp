package org.tavall.agentwebmcp.provider.process;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.dependency.annotations.DelegatesTo;
import org.tavall.internal.utils.concurrent.AsyncTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@DelegatesTo(CommandExecutor.class)
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
        try {
            Process startedProcess = new ProcessBuilder(command).start();
            process = startedProcess;
            CompletableFuture<byte[]> stdout = AsyncTask.supplyAsync(() -> processInput(startedProcess));
            CompletableFuture<byte[]> stderr = AsyncTask.supplyAsync(() -> processError(startedProcess));

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
            closeProcessStreams(process);
        }
    }

    private static byte[] processInput(Process process) {
        try {
            return process.getInputStream().readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read process stdout", exception);
        }
    }

    private static byte[] processError(Process process) {
        try {
            return process.getErrorStream().readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read process stderr", exception);
        }
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
