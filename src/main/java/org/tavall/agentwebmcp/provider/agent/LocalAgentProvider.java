package org.tavall.agentwebmcp.provider.agent;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.codex.CodexCliProvider;
import org.tavall.agentwebmcp.provider.codex.CodexStatus;
import org.tavall.dependency.IDependencyAccess;

import java.time.Instant;
import java.util.List;

public final class LocalAgentProvider implements AgentProvider, IDependencyAccess {
    public static final String LOCAL_CODEX_AGENT_ID = "codex:local";
    private static final List<String> CAPABILITIES = List.of(
            AgentCapabilities.SERVICE_JOB_PROMPT,
            AgentCapabilities.SERVICE_DISCOVERY_READ_ONLY
    );

    @Override
    public List<AgentSummary> listAgents() {
        CodexStatus status = getInstance(CodexCliProvider.class).status();
        if (!status.available()) {
            return List.of();
        }
        Instant heartbeat = Instant.now();
        return List.of(new AgentSummary(
                LOCAL_CODEX_AGENT_ID,
                "Installed Codex CLI",
                "ONLINE",
                "local",
                status.version(),
                heartbeat
        ));
    }

    @Override
    public AgentDetails inspectAgent(String agentId) {
        if (!LOCAL_CODEX_AGENT_ID.equals(agentId)) {
            throw new ProviderException("AGENT_NOT_FOUND", "Unknown agent: " + agentId, 404);
        }
        CodexStatus status = getInstance(CodexCliProvider.class).status();
        if (!status.available()) {
            throw new ProviderException("AGENT_NOT_FOUND", "Installed Codex CLI agent is unavailable", 404);
        }
        return new AgentDetails(
                LOCAL_CODEX_AGENT_ID,
                "Installed Codex CLI",
                "ONLINE",
                "local",
                "CODEX_CLI",
                status.version(),
                Instant.now(),
                CAPABILITIES
        );
    }
}
