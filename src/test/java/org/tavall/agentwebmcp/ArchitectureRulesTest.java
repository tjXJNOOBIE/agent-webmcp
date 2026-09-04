package org.tavall.agentwebmcp;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.handler.*;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.registry.AbstractRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {
    @Test
    void productionSourceAvoidsManagerShellAndUnmanagedConcurrencyAntiPatterns() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(path.getFileName().toString().contains("Manager"), () -> "Manager anti-pattern in " + path);
                assertFalse(source.contains("Runtime.getRuntime().exec"), () -> "Raw runtime exec in " + path);
                assertFalse(source.contains("new ProcessBuilder(\"sh\""), () -> "Shell trampoline in " + path);
                assertFalse(source.contains("new ProcessBuilder(\"bash\""), () -> "Shell trampoline in " + path);
                assertFalse(source.contains("java.util.concurrent.Executors"), () -> "Unmanaged executor import in " + path);
                assertFalse(source.contains("Executors."), () -> "Unmanaged executor creation in " + path);
                assertFalse(source.contains("Thread.ofVirtual()"), () -> "Feature-local virtual thread creation in " + path);
                if (path.getFileName().toString().equals("LocalJobProvider.java")) {
                    assertTrue(source.contains("AsyncTask.namingThreadFactory"),
                            "durable jobs must obtain interruptible worker threads through Tavall Concurrency");
                    assertTrue(source.contains("FutureTask<RunOutcome>"),
                            "durable job timeout must retain interruptible Future cancellation semantics");
                    assertTrue(source.contains("ICustomScheduler"),
                            "durable future/recurring jobs must use Tavall Scheduler");
                }
            }
        }
    }

    @Test
    void operationHandlersStayStatelessInsteadOfCapturingDependencies() {
        List<Class<?>> handlers = List.of(
                SystemStatusOperation.class, MetricsSnapshotOperation.class, TargetListOperation.class, TargetInspectOperation.class,
                AgentListOperation.class, AgentInspectOperation.class,
                ServiceListOperation.class, ServiceAddOperation.class, ServiceRemoveOperation.class, ServiceDiscoverOperation.class,
                ServiceInspectOperation.class, ServiceStatusOperation.class, ServiceLogsOperation.class, ServiceDiagnosticsOperation.class,
                ServiceStartOperation.class, ServiceStopOperation.class, ServiceRestartOperation.class, ServiceReloadOperation.class,
                JobListOperation.class, JobInspectOperation.class, JobLogsOperation.class, JobExecuteOperation.class, JobCancelOperation.class
        );
        handlers.forEach(handler -> {
            assertTrue(List.of(handler.getDeclaredFields()).stream().allMatch(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers())),
                    () -> handler.getSimpleName() + " captures state/dependencies");
            assertTrue(List.of(handler.getDeclaredConstructors()).stream().allMatch(constructor -> constructor.getParameterCount() == 0),
                    () -> handler.getSimpleName() + " uses constructor dependency injection");
        });
    }

    @Test
    void httpTransportStaysOnLightweightJdkServerAndTavallRuntimeOwnership() throws Exception {
        String server = Files.readString(Path.of("src/main/java/org/tavall/agentwebmcp/http/AgentWebMcpHttpServer.java"));
        String build = Files.readString(Path.of("build.gradle.kts")).toLowerCase();
        assertTrue(server.contains("com.sun.net.httpserver.HttpServer"));
        assertTrue(server.contains("AsyncTask"));
        assertTrue(server.contains("runtime().executor().execute"));
        assertTrue(server.contains("new McpHttpHandler()"));
        String mcp = Files.readString(Path.of("src/main/java/org/tavall/agentwebmcp/mcp/McpHttpHandler.java"));
        assertTrue(mcp.contains("runtime().executor().execute"));
        assertFalse(mcp.contains("ProcessBuilder"));
        assertTrue(mcp.contains("McpSessionCache"));
        assertFalse(mcp.contains("ConcurrentHashMap"), "MCP session state must use Tavall Cache, not an unbounded local map");
        for (String framework : List.of("spring-boot", "netty", "jetty", "undertow")) {
            assertFalse(build.contains(framework), () -> "Unexpected web framework dependency: " + framework);
        }
    }

    @Test
    void tavallToolsOwnCompositionRegistryConcurrencyAndSchedulingConcerns() throws Exception {
        assertTrue(IDependencyAccess.class.isAssignableFrom(AgentWebMcpRuntime.class));
        assertTrue(AbstractRegistry.class.isAssignableFrom(OperationCatalog.class));
        String settings = Files.readString(Path.of("settings.gradle.kts"));
        String build = Files.readString(Path.of("build.gradle.kts"));
        for (String artifact : List.of("tavall-di", "tavall-concurrency", "tavall-registry", "tavall-logging", "tavall-scheduler")) {
            assertTrue(settings.contains(artifact), () -> "Missing source mapping for " + artifact);
            assertTrue(build.contains("org.tavall:" + artifact), () -> "Missing dependency on " + artifact);
        }
        String sessionCache = Files.readString(Path.of("src/main/java/org/tavall/agentwebmcp/mcp/McpSessionCache.java"));
        assertTrue(build.contains("org.tavall:abstract-cache-system"), "MCP session expiry must use Tavall Cache");
        assertTrue(sessionCache.contains("extends AbstractCache"), "MCP session state must be owned by Tavall Cache");
        assertTrue(Files.exists(Path.of("scripts/ci/tavall-source-deps.tsv")));
        assertTrue(Files.exists(Path.of("scripts/ci/prepare-tavall-sources")));
    }
}
