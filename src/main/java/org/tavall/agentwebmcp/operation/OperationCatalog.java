package org.tavall.agentwebmcp.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OperationCatalog {
    private final Map<OperationId, OperationRegistration<?, ?>> registrations = new LinkedHashMap<>();

    public <I, R> void register(OperationRegistration<I, R> registration) {
        OperationId id = registration.descriptor().id();
        if (registrations.putIfAbsent(id, registration) != null) {
            throw new IllegalStateException("Duplicate operation id: " + id);
        }
    }

    public Optional<OperationRegistration<?, ?>> find(OperationId id) {
        return Optional.ofNullable(registrations.get(id));
    }

    public List<OperationRegistration<?, ?>> registrations() {
        List<OperationRegistration<?, ?>> ordered = new ArrayList<>(registrations.values());
        ordered.sort((left, right) -> left.descriptor().id().compareTo(right.descriptor().id()));
        return Collections.unmodifiableList(ordered);
    }

    public int size() {
        return registrations.size();
    }
}
