package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.EmptyInput;
import org.tavall.agentwebmcp.provider.target.TargetSummary;

import java.util.List;

public final class TargetListOperation implements OperationHandler<EmptyInput, List<TargetSummary>> {
    @Override
    public List<TargetSummary> execute(OperationContext context, EmptyInput input) {
        return context.targetProvider().listTargets();
    }
}
