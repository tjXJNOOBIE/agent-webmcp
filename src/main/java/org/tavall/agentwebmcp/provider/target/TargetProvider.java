package org.tavall.agentwebmcp.provider.target;

import java.util.List;

public interface TargetProvider {
    String providerName();

    List<TargetSummary> listTargets();

    TargetDetails inspectTarget(String targetId);
}
