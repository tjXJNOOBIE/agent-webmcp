package org.tavall.agentwebmcp.operation;

import org.junit.jupiter.api.Test;
import org.tavall.agentwebmcp.operation.input.EmptyInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationCatalogTest {
    @Test
    void rejectsDuplicateOperationIds() {
        OperationCatalog catalog = new OperationCatalog();
        OperationRegistration<EmptyInput, String> registration = new OperationRegistration<>(
                new OperationDescriptor(OperationId.of("test.read"), "Read a test value.", OperationAccess.READ_ONLY),
                EmptyInput.class,
                (context, input) -> "ok"
        );

        catalog.register(registration);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> catalog.register(registration));
        assertEquals("Duplicate operation id: test.read", exception.getMessage());
    }
}
