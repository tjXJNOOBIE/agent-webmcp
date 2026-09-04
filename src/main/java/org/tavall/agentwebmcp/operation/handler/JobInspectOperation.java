package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.JobIdInput;
import org.tavall.agentwebmcp.provider.job.JobDetails;

public final class JobInspectOperation implements OperationHandler<JobIdInput, JobDetails> {
    @Override
    public JobDetails execute(OperationContext context, JobIdInput input) {
        return context.jobProvider().inspectJob(input.jobId());
    }
}
