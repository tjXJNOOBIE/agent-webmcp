package org.tavall.agentwebmcp.provider.job;

import java.util.List;
import java.util.Optional;

public interface JobProvider extends AutoCloseable {
    String providerName();
    List<JobSummary> listJobs(int limit);
    JobDetails inspectJob(String jobId);
    JobLogSlice readLogs(String jobId, int lines, Optional<String> cursor);
    JobSubmission submit(JobRequest request);
    JobDetails cancel(String jobId);

    default void start() {
    }

    @Override
    default void close() {
    }
}
