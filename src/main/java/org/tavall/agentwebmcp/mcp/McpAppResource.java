package org.tavall.agentwebmcp.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static MCP Apps presentation resource for the bounded ChatGPT/MCP projection. */
public final class McpAppResource {
    public static final String URI = "ui://agent-webmcp/fleet-cockpit-v1";
    public static final String MIME_TYPE = "text/html;profile=mcp-app";
    public static final String EXTENSION_ID = "io.modelcontextprotocol/ui";
    private static final String DESCRIPTION = "Interactive Agent WebMCP Fleet Cockpit for managed-service health and lifecycle, metrics, runtime agents, and durable job evidence.";
    private static final String HTML = loadHtml();

    private McpAppResource() {
    }

    public static Map<String, Object> extensionSettings() {
        return Map.of("mimeTypes", List.of(MIME_TYPE));
    }

    public static Map<String, Object> descriptor() {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("uri", URI);
        descriptor.put("name", "agent_webmcp_fleet_cockpit");
        descriptor.put("description", DESCRIPTION);
        descriptor.put("mimeType", MIME_TYPE);
        descriptor.put("_meta", resourceMetadata());
        return descriptor;
    }

    public static Map<String, Object> content() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", URI);
        content.put("mimeType", MIME_TYPE);
        content.put("text", HTML);
        content.put("_meta", resourceMetadata());
        return content;
    }

    public static Map<String, Object> toolMetadata(boolean rendersApp) {
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("visibility", List.of("model", "app"));
        if (rendersApp) {
            ui.put("resourceUri", URI);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ui", ui);
        metadata.put("openai/widgetAccessible", true);
        if (rendersApp) {
            metadata.put("openai/outputTemplate", URI);
            metadata.put("openai/toolInvocation/invoking", "Opening Fleet Cockpit");
            metadata.put("openai/toolInvocation/invoked", "Fleet Cockpit ready");
        }
        return metadata;
    }

    private static Map<String, Object> resourceMetadata() {
        Map<String, Object> csp = new LinkedHashMap<>();
        csp.put("connectDomains", List.of());
        csp.put("resourceDomains", List.of());

        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("csp", csp);
        ui.put("prefersBorder", true);

        Map<String, Object> legacyCsp = new LinkedHashMap<>();
        legacyCsp.put("connect_domains", List.of());
        legacyCsp.put("resource_domains", List.of());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ui", ui);
        metadata.put("openai/widgetDescription", DESCRIPTION);
        metadata.put("openai/widgetPrefersBorder", true);
        metadata.put("openai/widgetCSP", legacyCsp);
        return metadata;
    }

    private static String loadHtml() {
        try (InputStream input = McpAppResource.class.getResourceAsStream("/web/mcp-app.html")) {
            if (input == null) {
                throw new IllegalStateException("Missing MCP Apps resource /web/mcp-app.html");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read MCP Apps resource", exception);
        }
    }
}
