package org.tavall.agentwebmcp.provider.process;

public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    public boolean successful() {
        return !timedOut && exitCode == 0;
    }
}
