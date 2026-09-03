package org.tavall.agentwebmcp.operation.input;

public record JobIdInput(String jobId) {
    public JobIdInput {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
    }
}
