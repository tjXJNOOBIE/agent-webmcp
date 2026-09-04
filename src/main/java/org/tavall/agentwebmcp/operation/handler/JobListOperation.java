package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.JobListInput;
import org.tavall.agentwebmcp.provider.job.JobSummary;

import java.util.List;

public final class JobListOperation implements OperationHandler<JobListInput, List<JobSummary>> {
    @Override
    public List<JobSummary> execute(OperationContext context, JobListInput input) {
        return context.jobProvider().listJobs(input.limit().orElse(100));
    }
}
