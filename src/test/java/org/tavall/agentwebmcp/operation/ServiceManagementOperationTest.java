package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceManagementOperationTest {
    @TempDir
    Path dataDirectory;

    @Test
    void managesExistingServiceWithoutOwningProviderUnitLifecycle() throws Exception {
        FakeServiceProvider services = new FakeServiceProvider();
        AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(services)
                .dataDirectory(dataDirectory)
                .build();

        OperationExecution initiallyEmpty = runtime.executor().execute("service.list", JsonNodeFactory.instance.objectNode());
        assertEquals(0, initiallyEmpty.output().size());

        OperationExecution blocked = runtime.executor().execute(
                "service.restart",
                JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")
        );
        assertEquals(OperationExecutionStatus.FAILURE, blocked.status());
        assertEquals("SERVICE_NOT_MANAGED", blocked.error().code());
        assertEquals("", services.lastAction());

        OperationExecution added = runtime.executor().execute(
                "service.add",
                JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")
        );
        assertEquals(OperationExecutionStatus.SUCCESS, added.status());
        assertTrue(added.output().path("changed").asBoolean());
        assertTrue(Files.readString(dataDirectory.resolve("managed-services.txt")).contains("demo.service"));

        OperationExecution listed = runtime.executor().execute("service.list", JsonNodeFactory.instance.objectNode());
        assertEquals(1, listed.output().size());
        assertEquals("demo.service", listed.output().get(0).path("id").asText());

        services.setMissing(true);
        OperationExecution missing = runtime.executor().execute("service.list", JsonNodeFactory.instance.objectNode());
        assertEquals(1, missing.output().size());
        assertEquals("UNKNOWN", missing.output().get(0).path("state").asText());
        assertEquals("not-found", missing.output().get(0).path("subState").asText());
        services.setMissing(false);

        OperationExecution stopped = runtime.executor().execute(
                "service.stop",
                JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")
        );
        assertEquals("STOPPED", stopped.output().path("observed").path("state").asText());

        OperationExecution removed = runtime.executor().execute(
                "service.remove",
                JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")
        );
        assertTrue(removed.output().path("changed").asBoolean());
        assertEquals(0, runtime.executor().execute("service.list", JsonNodeFactory.instance.objectNode()).output().size());

        // Removing from the panel does not delete the provider service or mutate it again.
        assertEquals("demo.service", services.inspectService("demo.service").id());
        assertEquals("stop", services.lastAction());
        assertFalse(Files.readString(dataDirectory.resolve("managed-services.txt")).contains("demo.service"));

        OperationExecution blockedAgain = runtime.executor().execute(
                "service.logs",
                JsonNodeFactory.instance.objectNode().put("serviceId", "demo.service")
        );
        assertEquals(OperationExecutionStatus.FAILURE, blockedAgain.status());
        assertEquals("SERVICE_NOT_MANAGED", blockedAgain.error().code());
    }
}
