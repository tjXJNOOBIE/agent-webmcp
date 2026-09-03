package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.TargetInspectInput;
import org.tavall.agentwebmcp.provider.target.TargetDetails;

public final class TargetInspectOperation implements OperationHandler<TargetInspectInput, TargetDetails> {
    @Override
    public TargetDetails execute(OperationContext context, TargetInspectInput input) {
        return context.targetProvider().inspectTarget(input.targetId());
    }
}
