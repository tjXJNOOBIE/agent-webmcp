package org.tavall.agentwebmcp.provider.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.process.CommandExecutor;
import org.tavall.agentwebmcp.provider.process.CommandResult;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@DelegatesTo(CodexCliProvider.class)
public final class LocalCodexCliProvider implements CodexCliProvider, IDependencyAccess {
    private static final int MAX_PROMPT_CHARS = 100_000;
    private static final String DISCOVERY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "serviceIds": {
                  "type": "array",
                  "items": { "type": "string" },
                  "maxItems": 200
                }
              },
              "required": ["serviceIds"],
              "additionalProperties": false
            }
            """;

    private final String binary = System.getenv().getOrDefault("AGENT_WEBMCP_CODEX_BIN", "codex").trim();

    @Override
    public CodexStatus status() {
        try {
            CommandResult result = command().execute(List.of(binary, "--version"), Duration.ofSeconds(5));
            return result.successful()
                    ? new CodexStatus(true, result.stdout().trim(), "installed")
                    : new CodexStatus(false, "", detail(result));
        } catch (ProviderException exception) {
            return new CodexStatus(false, "", exception.getMessage());
        }
    }

    @Override
    public CodexExecution executeServicePrompt(ServiceDetails service, String prompt, Duration timeout) {
        if (prompt == null || prompt.isBlank() || prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException("prompt must contain 1..100000 characters");
        }
        CodexStatus status = requireAvailable();
        Path serviceWorkingDirectory = workingDirectory(service).orElseThrow(() -> new ProviderException(
                "CODEX_SERVICE_WORKING_DIRECTORY_UNAVAILABLE",
                "Managed service does not expose a valid provider-owned WorkingDirectory",
                409
        ));
        List<String> argv = base("workspace-write");
        argv.add("-C");
        argv.add(serviceWorkingDirectory.toString());
        argv.add("-");
        String boundedPrompt = "Managed service: " + service.id()
                + "\nStay within this managed service context. Do not broaden host authority.\n\n"
                + prompt.trim();
        CommandResult result = command().execute(argv, timeout, boundedPrompt);
        if (result.timedOut()) {
            throw new ProviderException("CODEX_TIMEOUT", "Codex job timed out", 504);
        }
        if (!result.successful()) {
            throw new ProviderException("CODEX_EXECUTION_FAILED", detail(result), 502);
        }
        return new CodexExecution(result.stdout().trim(), result.stderr().trim(), result.exitCode(), status.version());
    }

    @Override
    public List<String> discoverServiceIds(Duration timeout) {
        requireAvailable();
        Path schema = null;
        try {
            schema = Files.createTempFile("agent-webmcp-codex-service-discovery-", ".schema.json");
            Files.writeString(schema, DISCOVERY_SCHEMA);
            List<String> argv = base("read-only");
            argv.add("--output-schema");
            argv.add(schema.toString());
            argv.add("-");
            String prompt = "Read-only discovery only. Inspect this machine for custom operator/application systemd services. "
                    + "Do not mutate anything. Return only the structured serviceIds result. Never return paths or commands.";
            CommandResult result = command().execute(argv, timeout, prompt);
            if (result.timedOut()) {
                throw new ProviderException("CODEX_DISCOVERY_TIMEOUT", "Codex discovery timed out", 504);
            }
            if (!result.successful()) {
                throw new ProviderException("CODEX_DISCOVERY_FAILED", detail(result), 502);
            }
            return parseDiscoveryResult(result.stdout());
        } catch (IOException exception) {
            throw new ProviderException("CODEX_DISCOVERY_SCHEMA_FAILED", "Unable to prepare bounded Codex discovery schema", 500);
        } finally {
            if (schema != null) {
                try {
                    Files.deleteIfExists(schema);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private List<String> parseDiscoveryResult(String output) {
        try {
            var root = mapper().readTree(output.trim());
            var ids = root.path("serviceIds");
            if (!root.isObject() || !ids.isArray() || root.size() != 1) {
                throw new IllegalArgumentException("invalid discovery object");
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (var node : ids) {
                if (!node.isTextual() || !node.asText().matches("[A-Za-z0-9_.@:-]+[.]service")) {
                    throw new IllegalArgumentException("invalid service id");
                }
                result.add(node.asText());
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new ProviderException("CODEX_DISCOVERY_INVALID_RESULT", "Codex discovery returned invalid structured serviceIds JSON", 502);
        }
    }

    private List<String> base(String sandbox) {
        return new ArrayList<>(List.of(
                binary, "exec",
                "--ephemeral",
                "--skip-git-repo-check",
                "--sandbox", sandbox,
                "--color", "never"
        ));
    }

    private CodexStatus requireAvailable() {
        CodexStatus status = status();
        if (!status.available()) {
            throw new ProviderException("CODEX_UNAVAILABLE", "Codex CLI is not available: " + status.detail(), 503);
        }
        return status;
    }

    private Optional<Path> workingDirectory(ServiceDetails service) {
        try {
            String configured = service.providerMetadata().getOrDefault("WorkingDirectory", "").trim();
            if (configured.isEmpty()) {
                return Optional.empty();
            }
            Path path = Path.of(configured).normalize();
            return path.isAbsolute() && !path.equals(Path.of("/")) && Files.isDirectory(path)
                    ? Optional.of(path)
                    : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private CommandExecutor command() {
        return getInstance(CommandExecutor.class);
    }

    private ObjectMapper mapper() {
        return getInstance(ObjectMapper.class);
    }

    private static String detail(CommandResult result) {
        String value = result.stderr().isBlank() ? result.stdout().trim() : result.stderr().trim();
        return value.isBlank() ? "Codex exited with code " + result.exitCode() : value;
    }
}
