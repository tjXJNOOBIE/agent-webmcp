package org.tavall.agentwebmcp.operation.handler;

import java.util.List;

public record ServiceDiscoveryResult(
        List<String> candidates,
        List<String> aiCandidates,
        List<String> registered,
        List<String> alreadyManaged,
        List<String> skipped,
        List<String> rejected,
        String provider,
        boolean aiRequested
) {
}
