package org.tavall.agentwebmcp.operation;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface OperationInvoker {
    OperationExecution execute(String operationId, JsonNode input);
}
