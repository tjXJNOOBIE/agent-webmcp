package org.tavall.agentwebmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationExecutor;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;
import org.tavall.dependency.maps.interfaces.IDependencyMap;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

class AgentWebMcpRuntimeTest {
    @TempDir
    Path dataDirectory;

    @Test
    void registersRuntimeDependenciesInTavallDi() {
        FakeServiceProvider services = new FakeServiceProvider();
        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .dataDirectory(dataDirectory)
                .build();

        IDependencyMap dependencies = runtime.dependencyMap();
        assertSame(runtime, dependencies.getInstance(AgentWebMcpRuntime.class));
        assertSame(runtime.objectMapper(), dependencies.getInstance(ObjectMapper.class));
        assertSame(runtime.catalog(), dependencies.getInstance(OperationCatalog.class));
        assertSame(runtime.context(), dependencies.getInstance(OperationContext.class));
        assertSame(runtime.executor(), dependencies.getInstance(OperationExecutor.class));
        assertSame(services, dependencies.getInstance(ServiceProvider.class));
    }
}
