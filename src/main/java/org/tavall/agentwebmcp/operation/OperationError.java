package org.tavall.agentwebmcp.operation;

public record OperationError(String code, String message, int httpStatus) {
}
