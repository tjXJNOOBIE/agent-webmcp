package org.tavall.agentwebmcp.operation.input;

public record JobIdInput(String jobId) {
    public JobIdInput {
        if (jobId == null || !jobId.matches("job-[a-f0-9]{12}")) throw new IllegalArgumentException("jobId is invalid");
    }
}
