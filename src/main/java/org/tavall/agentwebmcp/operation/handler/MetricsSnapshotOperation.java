package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.MetricsSnapshotInput;
import org.tavall.agentwebmcp.provider.metrics.SystemMetricsSnapshot;

public final class MetricsSnapshotOperation implements OperationHandler<MetricsSnapshotInput, MetricsSnapshotOperation.Result> {
    @Override
    public Result execute(OperationContext context, MetricsSnapshotInput input) {
        String targetId = OperationTargets.resolve(context, input.targetId());
        return new Result(targetId, context.metricsProvider().providerName(), context.metricsProvider().snapshot());
    }

    public record Result(String targetId, String provider, SystemMetricsSnapshot metrics) {
    }
}
