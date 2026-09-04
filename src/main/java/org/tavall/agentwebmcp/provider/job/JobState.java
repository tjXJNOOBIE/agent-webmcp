package org.tavall.agentwebmcp.provider.job;

public enum JobState {
    SCHEDULED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
