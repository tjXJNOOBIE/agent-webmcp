package org.tavall.agentwebmcp.operation;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.operation.schema.RecordJsonSchema;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultOperationCatalogTest {
    @Test
    void exposesCompleteOperationFamilies() {
        OperationCatalog catalog = DefaultOperationCatalog.create();
        List<String> ids = catalog.registrations().stream().map(registration -> registration.descriptor().id().value()).toList();

        assertEquals(21, ids.size());
        assertTrue(ids.containsAll(List.of(
                "system.status", "metrics.snapshot", "target.list", "target.inspect",
                "service.list", "service.add", "service.remove", "service.discover",
                "service.inspect", "service.status", "service.logs", "service.diagnostics",
                "service.start", "service.stop", "service.restart", "service.reload",
                "job.list", "job.inspect", "job.logs", "job.execute", "job.cancel"
        )));
        long mutating = catalog.registrations().stream()
                .filter(registration -> registration.descriptor().access() == OperationAccess.MUTATING)
                .count();
        assertEquals(9, mutating);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchemaIsDerivedFromTypedRecord() {
        OperationRegistration<?, ?> registration = DefaultOperationCatalog.create().registrations().stream()
                .filter(candidate -> candidate.descriptor().id().value().equals("service.inspect"))
                .findFirst().orElseThrow();

        Map<String, Object> schema = RecordJsonSchema.forType(registration.inputType());
        List<String> required = (List<String>) schema.get("required");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(required.contains("serviceId"));
        assertFalse(required.contains("targetId"));
        assertTrue(properties.containsKey("targetId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void jobExecutionSchemaIsServiceBoundAndKeepsStructuredInput() {
        OperationRegistration<?, ?> registration = DefaultOperationCatalog.create().registrations().stream()
                .filter(candidate -> candidate.descriptor().id().value().equals("job.execute"))
                .findFirst().orElseThrow();
        Map<String, Object> schema = RecordJsonSchema.forType(registration.inputType());
        List<String> required = (List<String>) schema.get("required");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(required.contains("serviceId"));
        assertEquals("object", ((Map<String, Object>) properties.get("input")).get("type"));
        assertTrue(properties.containsKey("prompt"));
        assertTrue(properties.containsKey("runAt"));
        assertTrue(properties.containsKey("repeatEverySeconds"));
    }
}
