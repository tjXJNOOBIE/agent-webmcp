package org.tavall.agentwebmcp.operation.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationException;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.OperationId;
import org.tavall.agentwebmcp.operation.input.JobExecuteInput;
import org.tavall.agentwebmcp.provider.agent.AgentCapabilities;
import org.tavall.agentwebmcp.provider.agent.AgentDetails;
import org.tavall.agentwebmcp.provider.job.JobKind;
import org.tavall.agentwebmcp.provider.job.JobRequest;
import org.tavall.agentwebmcp.provider.job.JobSubmission;

import java.util.Optional;
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
        Optional<String> resolvedAgentId = input.agentId();
        if (kind == JobKind.CODEX_PROMPT) {
            if (!context.codexCliProvider().status().available()) {
                throw new OperationException("CODEX_UNAVAILABLE", "Codex CLI is not installed or available to Agent WebMCP", 503);
            }
            resolvedAgentId = Optional.of(resolvePromptAgent(context, targetId, input.agentId()));
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
                resolvedAgentId
        ));
    }

    private static String resolvePromptAgent(OperationContext context, String targetId, Optional<String> requestedAgentId) {
        if (requestedAgentId.isPresent()) {
            AgentDetails requested = context.agentProvider().inspectAgent(requestedAgentId.get());
            requirePromptAgent(requested, targetId);
            return requested.id();
        }
        return context.agentProvider().listAgents().stream()
                .filter(agent -> targetId.equals(agent.targetId()))
                .map(agent -> context.agentProvider().inspectAgent(agent.id()))
                .filter(agent -> agent.capabilities().contains(AgentCapabilities.SERVICE_JOB_PROMPT))
                .map(AgentDetails::id)
                .findFirst()
                .orElseThrow(() -> new OperationException(
                        "CODEX_AGENT_UNAVAILABLE",
                        "No observed runtime agent can execute a service-scoped prompt for target " + targetId,
                        503
                ));
    }

    private static void requirePromptAgent(AgentDetails agent, String targetId) {
        if (!targetId.equals(agent.targetId())) {
            throw new OperationException("AGENT_TARGET_MISMATCH", "Selected agent is not bound to target " + targetId, 409);
        }
        if (!agent.capabilities().contains(AgentCapabilities.SERVICE_JOB_PROMPT)) {
            throw new OperationException("AGENT_CAPABILITY_MISMATCH", "Selected agent cannot execute service-scoped prompt jobs", 409);
        }
    }
}
