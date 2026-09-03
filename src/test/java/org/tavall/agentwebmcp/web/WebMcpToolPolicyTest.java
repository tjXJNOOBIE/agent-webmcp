package org.tavall.agentwebmcp.web;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.mcp.McpToolPolicy;
import org.tavall.agentwebmcp.operation.DefaultOperationCatalog;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WebMcpToolPolicyTest {
    @Test
    void projectsExplicitSixteenToolBrowserSurfaceAndFourteenToolMcpSurface() {
        var catalog = DefaultOperationCatalog.create();
        Set<String> web = catalog.registrations().stream().filter(WebMcpToolPolicy::allows)
                .map(registration -> registration.descriptor().id().value()).collect(Collectors.toSet());
        Set<String> mcp = catalog.registrations().stream().filter(McpToolPolicy::allows)
                .map(registration -> registration.descriptor().id().value()).collect(Collectors.toSet());

        assertEquals(16, web.size());
        assertEquals(14, mcp.size());
        assertTrue(web.containsAll(Set.of("service.add", "service.remove", "service.diagnostics", "job.list", "job.inspect", "job.logs")));
        assertFalse(web.contains("service.discover"));
        assertFalse(web.contains("job.execute"));
        assertFalse(web.contains("job.cancel"));
        assertFalse(web.stream().anyMatch(id -> id.startsWith("target.")));

        assertTrue(mcp.contains("service.diagnostics"));
        assertFalse(mcp.contains("service.add"));
        assertFalse(mcp.contains("service.remove"));
        assertFalse(mcp.contains("service.discover"));
        assertFalse(mcp.contains("job.execute"));
        assertFalse(mcp.contains("job.cancel"));
    }
}
