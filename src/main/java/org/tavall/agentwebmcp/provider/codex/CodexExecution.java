package org.tavall.agentwebmcp.provider.codex;

public record CodexExecution(String output, String stderr, int exitCode, String version) { }
