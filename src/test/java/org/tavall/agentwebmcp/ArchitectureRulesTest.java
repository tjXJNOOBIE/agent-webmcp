package org.tavall.agentwebmcp;

import org.junit.jupiter.api.Test;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {
    @Test
    void productionSourceAvoidsManagerAndShellExecutionAntiPatterns() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(path.getFileName().toString().contains("Manager"), () -> "Manager anti-pattern in " + path);
                assertFalse(source.contains("Runtime.getRuntime().exec"), () -> "Raw runtime exec in " + path);
                assertFalse(source.contains("new ProcessBuilder(\"sh\""), () -> "Shell trampoline in " + path);
                assertFalse(source.contains("new ProcessBuilder(\"bash\""), () -> "Shell trampoline in " + path);
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
}
