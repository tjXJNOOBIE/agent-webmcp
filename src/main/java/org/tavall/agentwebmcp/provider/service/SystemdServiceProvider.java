package org.tavall.agentwebmcp.provider.service;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.process.CommandExecutor;
import org.tavall.agentwebmcp.provider.process.CommandResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SystemdServiceProvider implements ServiceProvider {
    private static final Pattern SERVICE_ID = Pattern.compile("[A-Za-z0-9_.@:-]+");
    private final CommandExecutor commandExecutor;
    private final Duration commandTimeout;

    private SystemdServiceProvider(Builder builder) {
        this.commandExecutor = Objects.requireNonNull(builder.commandExecutor, "commandExecutor");
        this.commandTimeout = Objects.requireNonNull(builder.commandTimeout, "commandTimeout");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String providerName() {
        return "systemd";
    }

    @Override
    public boolean available() {
        try {
            return commandExecutor.execute(List.of("systemctl", "--version"), Duration.ofSeconds(3)).successful();
        } catch (ProviderException exception) {
            return false;
        }
    }

    @Override
    public List<ServiceSummary> listServices() {
        ensureAvailable();
        CommandResult result = run(List.of(
                "systemctl", "list-units", "--type=service", "--all", "--no-legend", "--no-pager", "--plain"
        ));
        List<ServiceSummary> services = new ArrayList<>();
        result.stdout().lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(line -> {
            String[] columns = line.split("\s+", 5);
            if (columns.length >= 4) {
                services.add(new ServiceSummary(
                        columns[0],
                        columns.length == 5 ? columns[4] : columns[0],
                        state(columns[2], columns[3]),
                        columns[3]
                ));
            }
        });
        return List.copyOf(services);
    }

    @Override
    public ServiceDetails inspectService(String serviceId) {
        ensureAvailable();
        String validServiceId = requireServiceId(serviceId);
        CommandResult result = run(List.of(
                "systemctl", "show", validServiceId, "--no-pager",
                "--property=Id,Description,LoadState,ActiveState,SubState,MainPID,MemoryCurrent,CPUUsageNSec"
        ));
        Map<String, String> properties = parseProperties(result.stdout());
        if (properties.getOrDefault("LoadState", "not-found").equals("not-found")) {
            throw new ProviderException("SERVICE_NOT_FOUND", "Unknown service: " + validServiceId, 404);
        }
        String activeState = properties.getOrDefault("ActiveState", "unknown");
        String subState = properties.getOrDefault("SubState", "unknown");
        return new ServiceDetails(
                properties.getOrDefault("Id", validServiceId),
                properties.getOrDefault("Description", validServiceId),
                state(activeState, subState),
                subState,
                parseLong(properties.get("MainPID")),
                parseLong(properties.get("MemoryCurrent")),
                parseLong(properties.get("CPUUsageNSec")),
                Map.copyOf(properties)
        );
    }

    @Override
    public ServiceMutationResult startService(String serviceId) {
        return mutate(serviceId, "start");
    }

    @Override
    public ServiceMutationResult stopService(String serviceId) {
        return mutate(serviceId, "stop");
    }

    @Override
    public ServiceMutationResult restartService(String serviceId) {
        return mutate(serviceId, "restart");
    }

    @Override
    public ServiceMutationResult reloadService(String serviceId) {
        return mutate(serviceId, "reload");
    }

    @Override
    public ServiceLogSlice readLogs(String serviceId, int lines, Optional<String> cursor) {
        ensureAvailable();
        String validServiceId = requireServiceId(serviceId);
        if (lines < 1 || lines > 1000) {
            throw new IllegalArgumentException("lines must be between 1 and 1000");
        }

        List<String> command = new ArrayList<>(List.of(
                "journalctl", "--unit", validServiceId, "--no-pager", "--output=short-iso",
                "--lines", Integer.toString(lines), "--show-cursor"
        ));
        cursor.filter(value -> !value.isBlank()).ifPresent(value -> command.add("--after-cursor=" + value));
        CommandResult result = run(command);

        String nextCursor = "";
        List<String> outputLines = new ArrayList<>();
        for (String line : result.stdout().split("\\R")) {
            if (line.startsWith("-- cursor: ")) {
                nextCursor = line.substring(11).trim();
            } else {
                outputLines.add(line);
            }
        }
        return new ServiceLogSlice(validServiceId, String.join(System.lineSeparator(), outputLines), nextCursor, lines);
    }

    private ServiceMutationResult mutate(String serviceId, String action) {
        ensureAvailable();
        String validServiceId = requireServiceId(serviceId);
        run(List.of("systemctl", action, validServiceId));
        return new ServiceMutationResult(validServiceId, action, inspectService(validServiceId));
    }

    private CommandResult run(List<String> command) {
        CommandResult result = commandExecutor.execute(List.copyOf(command), commandTimeout);
        if (result.timedOut()) {
            throw new ProviderException("PROVIDER_TIMEOUT", "Provider command timed out: " + command.getFirst(), 504);
        }
        if (!result.successful()) {
            String detail = !result.stderr().isBlank() ? result.stderr().trim() : result.stdout().trim();
            throw new ProviderException(
                    "PROVIDER_COMMAND_FAILED",
                    detail.isBlank() ? "Provider command failed with exit code " + result.exitCode() : detail,
                    502
            );
        }
        return result;
    }

    private void ensureAvailable() {
        if (!available()) {
            throw new ProviderException("SERVICE_PROVIDER_UNAVAILABLE", "systemd is not available on this target", 503);
        }
    }

    private static String requireServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank() || !SERVICE_ID.matcher(serviceId).matches()) {
            throw new IllegalArgumentException("serviceId contains unsupported characters");
        }
        return serviceId;
    }

    private static Map<String, String> parseProperties(String output) {
        Map<String, String> properties = new LinkedHashMap<>();
        output.lines().forEach(line -> {
            int separator = line.indexOf('=');
            if (separator > 0) {
                properties.put(line.substring(0, separator), line.substring(separator + 1));
            }
        });
        return properties;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank() || value.equals("[not set]")) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static ServiceState state(String activeState, String subState) {
        if ("failed".equals(activeState) || "failed".equals(subState)) {
            return ServiceState.FAILED;
        }
        if ("active".equals(activeState) && "running".equals(subState)) {
            return ServiceState.RUNNING;
        }
        if ("inactive".equals(activeState) || "dead".equals(subState)) {
            return ServiceState.STOPPED;
        }
        if ("activating".equals(activeState) || "reloading".equals(activeState) || "deactivating".equals(activeState)) {
            return ServiceState.DEGRADED;
        }
        return ServiceState.UNKNOWN;
    }

    public static final class Builder {
        private CommandExecutor commandExecutor;
        private Duration commandTimeout = Duration.ofSeconds(15);

        private Builder() {
        }

        public Builder commandExecutor(CommandExecutor commandExecutor) {
            this.commandExecutor = commandExecutor;
            return this;
        }

        public Builder commandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout;
            return this;
        }

        public SystemdServiceProvider build() {
            return new SystemdServiceProvider(this);
        }
    }
}
