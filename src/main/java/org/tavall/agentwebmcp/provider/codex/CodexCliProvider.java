package org.tavall.agentwebmcp.provider.codex;

import org.tavall.agentwebmcp.provider.service.ServiceDetails;

import java.time.Duration;
import java.util.List;

public interface CodexCliProvider {
    CodexStatus status();
    CodexExecution executeServicePrompt(ServiceDetails service, String prompt, Duration timeout);
    List<String> discoverServiceIds(Duration timeout);
}
