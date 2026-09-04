package org.tavall.agentwebmcp.service;

import java.util.regex.Pattern;

/** Canonical syntax guard for provider service identifiers passed as process arguments or durable state. */
public final class ServiceIdSyntax {
    private static final Pattern VALID = Pattern.compile("(?:[A-Za-z0-9_.@:-]|\\\\x[0-9A-Fa-f]{2})+");

    private ServiceIdSyntax() {
    }

    public static String require(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        String normalized = serviceId.trim();
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("serviceId contains unsupported characters");
        }
        return normalized;
    }

    public static boolean isValid(String serviceId) {
        try {
            require(serviceId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
