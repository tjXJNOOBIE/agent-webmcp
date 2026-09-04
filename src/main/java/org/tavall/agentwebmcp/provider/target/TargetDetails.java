package org.tavall.agentwebmcp.provider.target;

import java.util.Map;

public record TargetDetails(
        String id,
        String displayName,
        String hostname,
        String operatingSystem,
        String architecture,
        String javaVersion,
        Map<String, Object> capabilities
) {
}
