package org.tavall.agentwebmcp;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.handler.JobExecuteOperation;
import org.tavall.agentwebmcp.operation.handler.JobInspectOperation;
import org.tavall.agentwebmcp.operation.handler.JobListOperation;
import org.tavall.agentwebmcp.operation.handler.JobLogsOperation;
import org.tavall.agentwebmcp.operation.handler.MetricsSnapshotOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceInspectOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceListOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceLogsOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceReloadOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceRestartOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStartOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStatusOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStopOperation;
import org.tavall.agentwebmcp.operation.handler.SystemStatusOperation;
import org.tavall.agentwebmcp.operation.handler.TargetInspectOperation;
import org.tavall.agentwebmcp.operation.handler.TargetListOperation;
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
                    assertTrue(source.contains("FutureTask<OperationExecution>"),
                            "durable job timeout must retain interruptible Future cancellation semantics");
                }
            }
        }
    }

    @Test
    void operationHandlersStayStatelessInsteadOfCapturingDependencies() {
        List<Class<?>> handlers = List.of(
                SystemStatusOperation.class, MetricsSnapshotOperation.class, TargetListOperation.class, TargetInspectOperation.class,
                ServiceListOperation.class, ServiceInspectOperation.class, ServiceStatusOperation.class, ServiceLogsOperation.class,
                ServiceStartOperation.class, ServiceStopOperation.class, ServiceRestartOperation.class, ServiceReloadOperation.class,
                JobListOperation.class, JobInspectOperation.class, JobLogsOperation.class, JobExecuteOperation.class
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

        assertTrue(server.contains("com.sun.net.httpserver.HttpServer"),
                "HTTP edge must remain on the JDK lightweight server unless architecture explicitly changes");
        assertTrue(server.contains("AsyncTask"),
                "HTTP dispatch must use Tavall Concurrency");
        assertTrue(server.contains("runtime().executor().execute"),
                "HTTP execution must delegate to the canonical operation executor");
        for (String framework : List.of("spring-boot", "netty", "jetty", "undertow")) {
            assertFalse(build.contains(framework), () -> "Unexpected web framework dependency: " + framework);
        }
    }

    @Test
    void tavallToolsOwnCompositionRegistryAndConcurrencyConcerns() throws Exception {
        assertTrue(IDependencyAccess.class.isAssignableFrom(AgentWebMcpRuntime.class),
                "runtime composition must use Tavall DI");
        assertTrue(AbstractRegistry.class.isAssignableFrom(OperationCatalog.class),
                "operation catalog must build on Tavall Registry");
        assertFalse(Files.exists(Path.of("src/main/java/org/tavall/agentwebmcp/provider/job/JobStore.java")));
        assertFalse(Files.exists(Path.of("src/main/java/org/tavall/agentwebmcp/provider/job/FileJobStore.java")));

        String settings = Files.readString(Path.of("settings.gradle.kts"));
        String build = Files.readString(Path.of("build.gradle.kts"));
        for (String artifact : List.of("tavall-di", "tavall-concurrency", "tavall-registry", "tavall-logging")) {
            assertTrue(settings.contains(artifact), () -> "Missing source-control mapping for " + artifact);
            assertTrue(build.contains("org.tavall:" + artifact), () -> "Missing dependency on " + artifact);
        }
    }
}
