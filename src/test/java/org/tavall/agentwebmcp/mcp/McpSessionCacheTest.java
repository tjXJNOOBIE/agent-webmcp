package org.tavall.agentwebmcp.mcp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpSessionCacheTest {
    @Test
    void boundsLiveSessionsAndAllowsCapacityAfterExplicitClose() {
        McpSessionCache sessions = new McpSessionCache(1, TimeUnit.MINUTES, 1);
        assertTrue(sessions.open("mcp-a", "2025-06-18"));
        assertFalse(sessions.open("mcp-b", "2025-06-18"));
        assertEquals("2025-06-18", sessions.protocolVersion("mcp-a"));
        assertTrue(sessions.closeSession("mcp-a"));
        assertTrue(sessions.open("mcp-b", "2025-06-18"));
    }

    @Test
    void expiredSessionStopsAuthorizingProtocolState() throws InterruptedException {
        McpSessionCache sessions = new McpSessionCache(1, TimeUnit.MILLISECONDS, 4);
        assertTrue(sessions.open("mcp-expired", "2025-06-18"));
        TimeUnit.MILLISECONDS.sleep(5);
        assertNull(sessions.protocolVersion("mcp-expired"));
    }
}
