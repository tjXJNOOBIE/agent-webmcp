package org.tavall.agentwebmcp.operation.schema;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecordJsonSchema {
    private RecordJsonSchema() {
    }

    public static Map<String, Object> forType(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("Operation input type must be a record: " + type.getName());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            properties.put(component.getName(), componentSchema(component));
            if (component.getType() != Optional.class) {
                required.add(component.getName());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> componentSchema(RecordComponent component) {
        Type type = component.getGenericType();
        if (component.getType() == Optional.class && type instanceof ParameterizedType parameterizedType) {
            return scalarSchema(parameterizedType.getActualTypeArguments()[0]);
        }
        return scalarSchema(type);
    }

    private static Map<String, Object> scalarSchema(Type type) {
        Class<?> rawType = type instanceof Class<?> clazz ? clazz : Object.class;
        if (rawType == String.class) {
            return Map.of("type", "string");
        }
        if (rawType == Instant.class) {
            return Map.of("type", "string", "format", "date-time");
        }
        if (rawType == int.class || rawType == Integer.class || rawType == long.class || rawType == Long.class) {
            return Map.of("type", "integer");
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return Map.of("type", "boolean");
        }
        if (rawType.isEnum()) {
            List<String> values = java.util.Arrays.stream(rawType.getEnumConstants()).map(Object::toString).toList();
            return Map.of("type", "string", "enum", values);
        }
        return Map.of("type", "object");
    }
}
