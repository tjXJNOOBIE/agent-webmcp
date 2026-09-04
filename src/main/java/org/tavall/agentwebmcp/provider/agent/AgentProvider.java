package org.tavall.agentwebmcp.provider.agent;

import java.util.List;

public interface AgentProvider {
    List<AgentSummary> listAgents();
    AgentDetails inspectAgent(String agentId);
}
