package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationException;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.OperationId;
import org.tavall.agentwebmcp.operation.input.JobExecuteInput;
import org.tavall.agentwebmcp.provider.job.JobSubmission;

import java.time.Duration;

public final class JobExecuteOperation implements OperationHandler<JobExecuteInput, JobSubmission> {
    @Override
    public JobSubmission execute(OperationContext context, JobExecuteInput input) {
        OperationId operationId = OperationId.of(input.operationId());
        if (operationId.value().equals("job.execute")) {
            throw new OperationException("RECURSIVE_JOB_EXECUTION", "job.execute cannot schedule itself", 400);
        }
        if (context.catalog().find(operationId).isEmpty()) {
            throw new OperationException("OPERATION_NOT_FOUND", "Unknown operation: " + operationId.value(), 404);
        }
        return context.jobProvider().submit(
                operationId.value(),
                input.input(),
                Duration.ofSeconds(input.timeoutSeconds().orElse(60)),
                context.operationInvoker()
        );
    }
}
