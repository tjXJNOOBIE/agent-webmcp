package org.tavall.agentwebmcp.provider.process;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface CommandExecutor {
    CommandResult execute(List<String> command, Duration timeout);

    default CommandResult execute(List<String> command, Duration timeout, String stdin) {
        if (stdin != null && !stdin.isEmpty()) {
            throw new UnsupportedOperationException("This command executor does not support stdin");
        }
        return execute(command, timeout);
    }
}
