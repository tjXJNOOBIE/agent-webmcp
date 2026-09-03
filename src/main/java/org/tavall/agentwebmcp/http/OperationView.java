package org.tavall.agentwebmcp.http;

import org.tavall.agentwebmcp.operation.OperationRegistration;
import org.tavall.agentwebmcp.operation.schema.RecordJsonSchema;

import java.util.List;
import java.util.Map;

public record OperationView(
        String id,
        String description,
        String access,
        Map<String, Object> inputSchema,
        List<String> surfaces
) {
    public static OperationView from(OperationRegistration<?, ?> registration) {
        return new OperationView(
                registration.descriptor().id().value(),
                registration.descriptor().description(),
                registration.descriptor().access().name(),
                RecordJsonSchema.forType(registration.inputType()),
                List.of("CLI", "HTTP", "WEBMCP")
        );
    }
}
