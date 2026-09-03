package org.tavall.agentwebmcp.operation;

import java.util.Objects;
import java.util.regex.Pattern;

public record OperationId(String value) implements Comparable<OperationId> {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");

    public OperationId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid operation id: " + value);
        }
    }

    public static OperationId of(String value) {
        return new OperationId(value);
    }

    @Override
    public int compareTo(OperationId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
