package org.tavall.agentwebmcp.provider.job;

public record JobSubmission(String jobId, String operationId, JobState state, int timeoutSeconds) {
}
