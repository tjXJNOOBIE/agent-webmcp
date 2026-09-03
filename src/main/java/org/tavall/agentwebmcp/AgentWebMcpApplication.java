package org.tavall.agentwebmcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.tavall.agentwebmcp.http.AgentWebMcpHttpServer;
import org.tavall.agentwebmcp.http.OperationView;
import org.tavall.internal.utils.concurrent.AsyncTask;
import org.tavall.logging.Log;

import java.util.Arrays;
import java.util.List;

public final class AgentWebMcpApplication {
    private AgentWebMcpApplication() {
    }

    public static void main(String[] args) throws Exception {
        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.createDefault();
        String command = args.length == 0 ? "serve" : args[0];
        switch (command) {
            case "serve" -> serve(Arrays.copyOfRange(args, 1, args.length));
            case "operations" -> printOperations(runtime);
            case "execute" -> execute(runtime, Arrays.copyOfRange(args, 1, args.length));
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void serve(String[] args) throws InterruptedException {
        String host = option(args, "--host", environment("AGENT_WEBMCP_HOST", "127.0.0.1"));
        int port = Integer.parseInt(option(args, "--port", environment("AGENT_WEBMCP_PORT", "7188")));
        AgentWebMcpHttpServer server = AgentWebMcpHttpServer.builder()
                .host(host)
                .port(port)
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            AsyncTask.shutdown();
        }, "agent-webmcp-shutdown"));
        server.start();
        Log.success("Agent WebMCP listening at http://" + server.host() + ":" + server.port());
        Log.warn("NO_AUTH is active. Keep this endpoint behind the trusted local/private tunnel boundary you control.");
        Thread.currentThread().join();
    }

    private static void printOperations(AgentWebMcpRuntime runtime) throws Exception {
        List<OperationView> views = runtime.catalog().registrations().stream().map(OperationView::from).toList();
        runtime.objectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, views);
        System.out.println();
    }

    private static void execute(AgentWebMcpRuntime runtime, String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("execute requires an operation id");
        }
        JsonNode input = args.length >= 2
                ? runtime.objectMapper().readTree(args[1])
                : JsonNodeFactory.instance.objectNode();
        var execution = runtime.executor().execute(args[0], input);
        runtime.objectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, execution);
        System.out.println();
        if (execution.error() != null) {
            System.exit(1);
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String option(String[] args, String name, String fallback) {
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.startsWith(name + "=")) {
                return argument.substring(name.length() + 1);
            }
            if (argument.equals(name) && index + 1 < args.length) {
                return args[index + 1];
            }
        }
        return fallback;
    }
}
