package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;

import java.time.Duration;
import java.time.Instant;

@DelegatesTo
public final class OperationExecutor implements IDependencyAccess {
    public OperationExecution execute(String rawOperationId, JsonNode rawInput) {
        Instant startedAt = Instant.now();
        OperationId operationId;
        try {
            operationId = OperationId.of(rawOperationId);
        } catch (RuntimeException exception) {
            return failure(rawOperationId, startedAt, "INVALID_OPERATION_ID", exception.getMessage(), 400);
        }

        OperationRegistration<?, ?> registration = catalog().find(operationId).orElse(null);
        if (registration == null) {
            return failure(rawOperationId, startedAt, "OPERATION_NOT_FOUND", "Unknown operation: " + rawOperationId, 404);
        }

        try {
            JsonNode inputNode = rawInput == null || rawInput.isNull() ? JsonNodeFactory.instance.objectNode() : rawInput;
            Object input = objectMapper().treeToValue(inputNode, registration.inputType());
            Object output = invoke(registration, input);
            Instant completedAt = Instant.now();
            return new OperationExecution(
                    operationId.value(),
                    OperationExecutionStatus.SUCCESS,
                    startedAt,
                    completedAt,
                    Duration.between(startedAt, completedAt).toMillis(),
                    objectMapper().valueToTree(output),
                    null
            );
        } catch (OperationException exception) {
            return failure(operationId.value(), startedAt, exception.code(), exception.getMessage(), exception.httpStatus());
        } catch (ProviderException exception) {
            return failure(operationId.value(), startedAt, exception.code(), exception.getMessage(), exception.httpStatus());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            return failure(operationId.value(), startedAt, "INVALID_INPUT", exception.getMessage(), 400);
        } catch (Exception exception) {
            return failure(operationId.value(), startedAt, "OPERATION_FAILED", safeMessage(exception), 500);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object invoke(OperationRegistration registration, Object input) throws Exception {
        return registration.handler().execute(context(), input);
    }

    private OperationCatalog catalog() {
        return getInstance(OperationCatalog.class);
    }

    private ObjectMapper objectMapper() {
        return getInstance(ObjectMapper.class);
    }

    private OperationContext context() {
        return getInstance(OperationContext.class);
    }

    private static OperationExecution failure(String operationId, Instant startedAt, String code, String message, int httpStatus) {
        Instant completedAt = Instant.now();
        return new OperationExecution(
                operationId,
                OperationExecutionStatus.FAILURE,
                startedAt,
                completedAt,
                Duration.between(startedAt, completedAt).toMillis(),
                null,
                new OperationError(code, message == null ? code : message, httpStatus)
        );
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
