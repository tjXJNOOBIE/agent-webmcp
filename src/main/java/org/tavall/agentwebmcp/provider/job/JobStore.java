package org.tavall.agentwebmcp.provider.job;

import java.util.List;
import java.util.function.UnaryOperator;

public interface JobStore {
    List<JobRecord> list();

    JobRecord read(String jobId);

    void write(JobRecord job);

    JobRecord update(String jobId, UnaryOperator<JobRecord> updater);
}
