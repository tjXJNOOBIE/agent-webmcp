package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.JobLogsInput;
import org.tavall.agentwebmcp.provider.job.JobLogSlice;

public final class JobLogsOperation implements OperationHandler<JobLogsInput, JobLogSlice> {
    @Override
    public JobLogSlice execute(OperationContext context, JobLogsInput input) {
        return context.jobProvider().readLogs(input.jobId(), input.lines().orElse(100), input.cursor());
    }
}
