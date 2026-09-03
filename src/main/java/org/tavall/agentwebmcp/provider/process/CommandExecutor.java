package org.tavall.agentwebmcp.provider.process;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface CommandExecutor {
    CommandResult execute(List<String> command, Duration timeout);
}
