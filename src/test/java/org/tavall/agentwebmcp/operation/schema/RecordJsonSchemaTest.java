package org.tavall.agentwebmcp.operation.schema;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecordJsonSchemaTest {
    @Test
    void optionalInstantUsesJsonSchemaDateTimeString() {
        Map<String, Object> schema = RecordJsonSchema.forType(TemporalInput.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertEquals(Map.of("type", "string", "format", "date-time"), properties.get("runAt"));
        assertFalse(((java.util.List<?>) schema.getOrDefault("required", java.util.List.of())).contains("runAt"));
    }

    private record TemporalInput(Optional<Instant> runAt) {
    }
}
