package org.tavall.agentwebmcp.provider.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.process.CommandExecutor;
import org.tavall.agentwebmcp.provider.process.CommandResult;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceState;
import org.tavall.dependency.maps.DependencyMap;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalCodexCliProviderTest {
    @TempDir Path workingDirectory;

    @Test
    void executesPromptThroughStdinInProviderOwnedWorkingDirectory() {
        FakeCommands commands = registerCommands();
        LocalCodexCliProvider provider = new LocalCodexCliProvider();

        CodexExecution result = provider.executeServicePrompt(service(Map.of("WorkingDirectory", workingDirectory.toString())), "inspect the service", Duration.ofSeconds(5));

        assertEquals("done", result.output());
        assertTrue(commands.lastCommand.containsAll(List.of("exec", "--ephemeral", "--skip-git-repo-check", "--sandbox", "workspace-write", "-C", workingDirectory.toString(), "-")));
        assertFalse(commands.lastCommand.contains("--ask-for-approval"));
        assertTrue(commands.lastStdin.contains("Managed service: demo.service"));
        assertTrue(commands.lastStdin.endsWith("inspect the service"));
    }

    @Test
    void requiresRealProviderWorkingDirectoryAndBoundsPrompt() {
        registerCommands();
        LocalCodexCliProvider provider = new LocalCodexCliProvider();
        ProviderException missingDirectory = assertThrows(ProviderException.class, () -> provider.executeServicePrompt(
                service(Map.of()), "inspect", Duration.ofSeconds(5)));
        assertEquals("CODEX_SERVICE_WORKING_DIRECTORY_UNAVAILABLE", missingDirectory.code());
        assertThrows(IllegalArgumentException.class, () -> provider.executeServicePrompt(
                service(Map.of("WorkingDirectory", workingDirectory.toString())), "x".repeat(100_001), Duration.ofSeconds(5)));
    }

    @Test
    void discoveryUsesReadOnlySandboxAndStrictStructuredIds() {
        FakeCommands commands = registerCommands();
        commands.execStdout = "{\"serviceIds\":[\"demo.service\",\"opt-worker.service\"]}";
        LocalCodexCliProvider provider = new LocalCodexCliProvider();

        assertEquals(List.of("demo.service", "opt-worker.service"), provider.discoverServiceIds(Duration.ofSeconds(5)));
        assertTrue(commands.lastCommand.containsAll(List.of("--sandbox", "read-only", "--output-schema", "-")));
        assertFalse(commands.lastCommand.contains("workspace-write"));
        assertTrue(commands.lastStdin.contains("Do not mutate anything"));
    }

    @Test
    void invalidDiscoveryJsonAndMissingCodexAreTypedFailures() {
        FakeCommands commands = registerCommands();
        commands.execStdout = "not-json";
        ProviderException invalid = assertThrows(ProviderException.class, () -> new LocalCodexCliProvider().discoverServiceIds(Duration.ofSeconds(5)));
        assertEquals("CODEX_DISCOVERY_INVALID_RESULT", invalid.code());

        commands.missing = true;
        LocalCodexCliProvider missingProvider = new LocalCodexCliProvider();
        assertFalse(missingProvider.status().available());
        ProviderException unavailable = assertThrows(ProviderException.class, () -> missingProvider.discoverServiceIds(Duration.ofSeconds(5)));
        assertEquals("CODEX_UNAVAILABLE", unavailable.code());
    }

    private FakeCommands registerCommands() {
        FakeCommands commands = new FakeCommands();
        DependencyMap dependencies = DependencyMap.getDependencyMap();
        dependencies.registerInstance(CommandExecutor.class, commands);
        dependencies.registerInstance(ObjectMapper.class, new ObjectMapper().findAndRegisterModules());
        return commands;
    }

    private static ServiceDetails service(Map<String, String> metadata) {
        return new ServiceDetails("demo.service", "Demo", ServiceState.RUNNING, "running", 42, 1, 1, metadata);
    }

    private static final class FakeCommands implements CommandExecutor {
        private boolean missing;
        private String execStdout = "done";
        private List<String> lastCommand = new ArrayList<>();
        private String lastStdin = "";

        @Override
        public CommandResult execute(List<String> command, Duration timeout) {
            if (missing) throw new ProviderException("PROCESS_START_FAILED", "missing", 503);
            lastCommand = List.copyOf(command);
            if (command.contains("--version")) return new CommandResult(0, "codex-cli 0.144.5", "", false);
            return new CommandResult(0, execStdout, "", false);
        }

        @Override
        public CommandResult execute(List<String> command, Duration timeout, String stdin) {
            if (missing) throw new ProviderException("PROCESS_START_FAILED", "missing", 503);
            lastCommand = List.copyOf(command);
            lastStdin = stdin;
            return new CommandResult(0, execStdout, "", false);
        }
    }
}
