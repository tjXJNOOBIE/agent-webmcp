package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.EmptyInput;
import org.tavall.agentwebmcp.provider.agent.AgentSummary;

import java.util.List;

public final class AgentListOperation implements OperationHandler<EmptyInput, List<AgentSummary>> {
    @Override
    public List<AgentSummary> execute(OperationContext context, EmptyInput input) {
        return context.agentProvider().listAgents();
    }
}
