package org.tavall.agentwebmcp.operation.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationException;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.OperationId;
import org.tavall.agentwebmcp.operation.input.JobExecuteInput;
import org.tavall.agentwebmcp.provider.job.JobKind;
import org.tavall.agentwebmcp.provider.job.JobRequest;
import org.tavall.agentwebmcp.provider.job.JobSubmission;

import java.util.Set;

public final class JobExecuteOperation implements OperationHandler<JobExecuteInput, JobSubmission> {
    private static final Set<String> DETERMINISTIC_SERVICE_OPERATIONS = Set.of(
            "service.start",
            "service.stop",
            "service.restart",
            "service.reload"
    );

    @Override
    public JobSubmission execute(OperationContext context, JobExecuteInput input) {
        String targetId = OperationTargets.resolve(context, input.targetId());
        ManagedServiceAccess.requireManaged(context, input.serviceId());
        context.serviceProvider().inspectService(input.serviceId());

        JobKind kind = input.prompt().isPresent() ? JobKind.CODEX_PROMPT : JobKind.SERVICE_OPERATION;
        String operationId = input.operationId().orElse("codex.prompt");
        if (kind == JobKind.CODEX_PROMPT) {
            if (!context.codexCliProvider().status().available()) {
                throw new OperationException("CODEX_UNAVAILABLE", "Codex CLI is not installed or available to Agent WebMCP", 503);
            }
        } else {
            if (!DETERMINISTIC_SERVICE_OPERATIONS.contains(operationId)) {
                throw new OperationException("JOB_OPERATION_NOT_ALLOWED", "Deterministic jobs may execute only explicit managed-service lifecycle operations", 400);
            }
            if (context.catalog().find(OperationId.of(operationId)).isEmpty()) {
                throw new OperationException("OPERATION_NOT_FOUND", "Unknown operation: " + operationId, 404);
            }
        }

        ObjectNode operationInput = input.input().deepCopy();
        operationInput.put("serviceId", input.serviceId());
        if (!"local".equals(targetId)) {
            operationInput.put("targetId", targetId);
        }
        return context.jobProvider().submit(new JobRequest(
                targetId,
                input.serviceId(),
                kind,
                operationId,
                operationInput,
                input.prompt(),
                input.runAt(),
                input.repeatEverySeconds(),
                input.timeoutSeconds().orElse(kind == JobKind.CODEX_PROMPT ? 900 : 60),
                input.agentId()
        ));
    }
}
