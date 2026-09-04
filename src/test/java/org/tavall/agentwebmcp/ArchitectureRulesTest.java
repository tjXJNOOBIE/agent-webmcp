package org.tavall.agentwebmcp;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.handler.*;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.registry.AbstractRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {
    @Test
    void productionSourceAvoidsManagerShellAndUnmanagedConcurrencyAntiPatterns() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                List<String> violations = sourcePolicyViolations(path, source);
                assertTrue(violations.isEmpty(), () -> path + ": " + String.join(", ", violations));
            }
        }
    }

    @Test
    void sourcePolicyFixturesProveRejectedAndAcceptedShapes() {
        List<Fixture> invalid = List.of(
                new Fixture(Path.of("ExampleManager.java"), "final class ExampleManager {}"),
                new Fixture(Path.of("ExampleService.java"), "Runtime.getRuntime().exec(\"whoami\");"),
                new Fixture(Path.of("ExampleService.java"), "new ProcessBuilder(\"sh\", \"-c\", \"echo bad\");"),
                new Fixture(Path.of("ExampleService.java"), "new ProcessBuilder(\"bash\", \"-c\", \"echo bad\");"),
                new Fixture(Path.of("ExampleService.java"), "import java.util.concurrent.Executors;"),
                new Fixture(Path.of("ExampleService.java"), "Executors.newFixedThreadPool(4);"),
                new Fixture(Path.of("ExampleService.java"), "Thread.ofVirtual().start(() -> {});")
        );
        invalid.forEach(fixture -> assertFalse(sourcePolicyViolations(fixture.path(), fixture.source()).isEmpty(),
                () -> "fixture should violate source policy: " + fixture.source()));

        assertTrue(sourcePolicyViolations(
                Path.of("ExampleService.java"),
                "final class ExampleService { void run() { AsyncTask.runAsync(() -> {}); } }"
        ).isEmpty());

        String validJobShape = "AsyncTask.namingThreadFactory FutureTask<RunOutcome> ICustomScheduler";
        assertTrue(sourcePolicyViolations(Path.of("LocalJobProvider.java"), validJobShape).isEmpty());
        assertFalse(sourcePolicyViolations(Path.of("LocalJobProvider.java"), "ICustomScheduler").isEmpty());
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

    private static List<String> sourcePolicyViolations(Path path, String source) {
        List<String> violations = new ArrayList<>();
        if (path.getFileName().toString().contains("Manager")) violations.add("Manager anti-pattern");
        if (source.contains("Runtime.getRuntime().exec")) violations.add("raw Runtime.exec");
        if (source.contains("new ProcessBuilder(\"sh\"")) violations.add("sh shell trampoline");
        if (source.contains("new ProcessBuilder(\"bash\"")) violations.add("bash shell trampoline");
        if (source.contains("java.util.concurrent.Executors")) violations.add("unmanaged executor import");
        if (source.contains("Executors.")) violations.add("unmanaged executor creation");
        if (source.contains("Thread.ofVirtual()")) violations.add("feature-local virtual thread");
        if (path.getFileName().toString().equals("LocalJobProvider.java")) {
            if (!source.contains("AsyncTask.namingThreadFactory")) violations.add("job worker bypasses Tavall Concurrency");
            if (!source.contains("FutureTask<RunOutcome>")) violations.add("job timeout lacks cancellable FutureTask");
            if (!source.contains("ICustomScheduler")) violations.add("job scheduling bypasses Tavall Scheduler");
        }
        return List.copyOf(violations);
    }

    private record Fixture(Path path, String source) {
    }
}
