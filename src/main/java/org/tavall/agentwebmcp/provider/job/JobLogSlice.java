package org.tavall.agentwebmcp.provider.job;

import java.util.List;

public record JobLogSlice(
        String jobId,
        List<JobLogEntry> entries,
        String cursor,
        int requestedLines
) {
}
