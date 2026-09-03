package org.tavall.agentwebmcp.provider.job;

import java.util.Optional;

public record JobSubmission(String jobId, String operationId, Optional<String> agentId, JobState state, int timeoutSeconds) {
}
