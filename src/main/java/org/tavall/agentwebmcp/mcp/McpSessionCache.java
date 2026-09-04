package org.tavall.agentwebmcp.mcp;

import org.tavall.abstractcache.cache.AbstractCache;

import java.util.concurrent.TimeUnit;

/**
 * Bounded expiring transport state for session-oriented MCP clients.
 *
 * <p>MCP session IDs are disposable protocol state, not application authority or durable truth.
 * Tavall Cache owns expiry/storage semantics while this adapter adds the protocol-specific capacity
 * bound and sliding activity TTL.</p>
 */
final class McpSessionCache extends AbstractCache<String, String> {
    static final long DEFAULT_TTL_MINUTES = 30;
    static final int DEFAULT_MAX_SESSIONS = 1_024;

    private final int maximumSessions;

    McpSessionCache() {
        this(DEFAULT_TTL_MINUTES, TimeUnit.MINUTES, DEFAULT_MAX_SESSIONS);
    }

    McpSessionCache(long ttl, TimeUnit unit, int maximumSessions) {
        super(ttl, unit);
        if (ttl < 0) {
            throw new IllegalArgumentException("ttl must be non-negative");
        }
        if (maximumSessions < 1) {
            throw new IllegalArgumentException("maximumSessions must be positive");
        }
        this.maximumSessions = maximumSessions;
    }

    synchronized boolean open(String sessionId, String protocolVersion) {
        cleanupExpired();
        if (size() >= maximumSessions) {
            return false;
        }
        put(sessionId, protocolVersion);
        return true;
    }

    String protocolVersion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String protocolVersion = getIfPresent(sessionId, null, null, null, null);
        if (protocolVersion != null) {
            put(sessionId, protocolVersion);
        }
        return protocolVersion;
    }

    boolean closeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return remove(sessionId, null, null, null, null) != null;
    }
}
