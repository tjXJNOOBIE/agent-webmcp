package org.tavall.agentwebmcp.operation;

import org.tavall.dependency.annotations.DelegatesTo;
import org.tavall.registry.AbstractRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@DelegatesTo
public final class OperationCatalog extends AbstractRegistry<OperationId, OperationRegistration<?, ?>> {
    public <I, R> void register(OperationRegistration<I, R> registration) {
        OperationId id = registration.descriptor().id();
        if (putIfAbsent(id, registration) != null) {
            throw new IllegalStateException("Duplicate operation id: " + id);
        }
    }

    public Optional<OperationRegistration<?, ?>> find(OperationId id) {
        return Optional.ofNullable(get(id));
    }

    public List<OperationRegistration<?, ?>> registrations() {
        List<OperationRegistration<?, ?>> ordered = new ArrayList<>(values());
        ordered.sort((left, right) -> left.descriptor().id().compareTo(right.descriptor().id()));
        return Collections.unmodifiableList(ordered);
    }
}
