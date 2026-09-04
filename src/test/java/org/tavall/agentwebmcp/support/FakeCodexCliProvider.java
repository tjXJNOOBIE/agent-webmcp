package org.tavall.agentwebmcp.support;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.codex.CodexCliProvider;
import org.tavall.agentwebmcp.provider.codex.CodexExecution;
import org.tavall.agentwebmcp.provider.codex.CodexStatus;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;

import java.time.Duration;
import java.util.List;

public final class FakeCodexCliProvider implements CodexCliProvider {
    private boolean available = true;
    private List<String> discoveredServiceIds = List.of();
    private String output = "fake Codex execution complete";
    private String lastPrompt = "";
    private String lastServiceId = "";

    @Override
    public CodexStatus status() {
        return available
                ? new CodexStatus(true, "codex-cli test", "installed")
                : new CodexStatus(false, "", "not installed");
    }

    @Override
    public CodexExecution executeServicePrompt(ServiceDetails service, String prompt, Duration timeout) {
        if (!available) {
            throw new ProviderException("CODEX_UNAVAILABLE", "Codex CLI is unavailable", 503);
        }
        lastServiceId = service.id();
        lastPrompt = prompt;
        return new CodexExecution(output, "", 0, "codex-cli test");
    }

    @Override
    public List<String> discoverServiceIds(Duration timeout) {
        if (!available) {
            throw new ProviderException("CODEX_UNAVAILABLE", "Codex CLI is unavailable", 503);
        }
        return discoveredServiceIds;
    }

    public void setAvailable(boolean available) { this.available = available; }
    public void setDiscoveredServiceIds(List<String> discoveredServiceIds) { this.discoveredServiceIds = List.copyOf(discoveredServiceIds); }
    public void setOutput(String output) { this.output = output; }
    public String lastPrompt() { return lastPrompt; }
    public String lastServiceId() { return lastServiceId; }
}
