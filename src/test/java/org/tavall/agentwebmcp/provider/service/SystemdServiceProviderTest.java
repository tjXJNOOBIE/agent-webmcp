package org.tavall.agentwebmcp.provider.service;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.provider.process.CommandExecutor;
import org.tavall.agentwebmcp.provider.process.CommandResult;
import org.tavall.dependency.maps.DependencyMap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemdServiceProviderTest {
    @Test
    void restartUsesArgumentVectorAndReturnsObservedState() {
        FakeCommandExecutor commands = registerCommands();
        SystemdServiceProvider provider = SystemdServiceProvider.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();

        ServiceMutationResult result = provider.restartService("demo.service");

        assertEquals("restart", result.action());
        assertEquals(ServiceState.RUNNING, result.observed().state());
        assertTrue(commands.commands.contains(List.of("systemctl", "restart", "demo.service")));
    }

    @Test
    void rejectsShellLikeServiceIdentifiersBeforeExecution() {
        FakeCommandExecutor commands = registerCommands();
        SystemdServiceProvider provider = SystemdServiceProvider.builder().build();
        int before = commands.commands.size();

        assertThrows(IllegalArgumentException.class, () -> provider.restartService("demo.service;rm"));
        assertEquals(before + 1, commands.commands.size(), "availability probe is allowed; mutation command is not");
    }

    @Test
    void logsAreBoundedAndCursorIsParsed() {
        FakeCommandExecutor commands = registerCommands();
        SystemdServiceProvider provider = SystemdServiceProvider.builder().build();

        ServiceLogSlice logs = provider.readLogs("demo.service", 25, Optional.of("cursor-old"));

        assertEquals(25, logs.requestedLines());
        assertEquals("cursor-next", logs.cursor());
        assertTrue(logs.output().contains("first"));
        assertTrue(commands.commands.stream().anyMatch(command -> command.contains("--after-cursor=cursor-old")));
        assertThrows(IllegalArgumentException.class, () -> provider.readLogs("demo.service", 1001, Optional.empty()));
    }

    private static FakeCommandExecutor registerCommands() {
        FakeCommandExecutor commands = new FakeCommandExecutor();
        DependencyMap.getDependencyMap().registerInstance(CommandExecutor.class, commands);
        return commands;
    }

    private static final class FakeCommandExecutor implements CommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();

        @Override
        public CommandResult execute(List<String> command, Duration timeout) {
            commands.add(List.copyOf(command));
            if (command.equals(List.of("systemctl", "--version"))) {
                return new CommandResult(0, "systemd 258", "", false);
            }
            if (command.size() >= 2 && command.get(0).equals("systemctl") && command.get(1).equals("show")) {
                return new CommandResult(0, "Id=demo.service\nDescription=Demo service\nLoadState=loaded\nActiveState=active\nSubState=running\nMainPID=42\nMemoryCurrent=1024\nCPUUsageNSec=2048\n", "", false);
            }
            if (command.get(0).equals("journalctl")) {
                return new CommandResult(0, "first\nsecond\n-- cursor: cursor-next\n", "", false);
            }
            return new CommandResult(0, "", "", false);
        }
    }
}
