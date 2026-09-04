package org.tavall.agentwebmcp.http;

import com.sun.net.httpserver.HttpExchange;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;

public final class HttpRequestSecurityPolicy {
    private static final Set<String> DEFAULT_ALLOWED_ORIGINS = Set.of(
            "https://chatgpt.com",
            "https://chat.openai.com"
    );

    private HttpRequestSecurityPolicy() {
    }

    public static boolean originAllowed(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }

        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            return false;
        }
        if (isLoopbackHost(host)) {
            return true;
        }

        String requestHost = exchange.getRequestHeaders().getFirst("Host");
        if (requestHost != null && requestHost.equalsIgnoreCase(uri.getAuthority())) {
            return true;
        }

        String configured = System.getenv("AGENT_WEBMCP_ALLOWED_ORIGINS");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_ALLOWED_ORIGINS.contains(origin);
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(origin::equals);
    }

    public static boolean hasJsonContentType(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return false;
        }
        int separator = contentType.indexOf(';');
        String mediaType = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return "application/json".equalsIgnoreCase(mediaType.trim());
    }

    public static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            return addresses.length > 0 && Arrays.stream(addresses).allMatch(InetAddress::isLoopbackAddress);
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
