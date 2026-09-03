package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.tavall.agentwebmcp.operation.OperationInvoker;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface JobProvider {
    String providerName();

    List<JobSummary> listJobs(int limit);

    JobDetails inspectJob(String jobId);

    JobLogSlice readLogs(String jobId, int lines, Optional<String> cursor);

    JobSubmission submit(String operationId, JsonNode input, Duration timeout, Optional<String> agentId, OperationInvoker operationInvoker);
}
