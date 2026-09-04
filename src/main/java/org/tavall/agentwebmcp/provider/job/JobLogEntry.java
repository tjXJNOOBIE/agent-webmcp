package org.tavall.agentwebmcp.provider.job;

import java.time.Instant;

public record JobLogEntry(Instant at, String level, String message) {
}
