package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.AgentInspectInput;
import org.tavall.agentwebmcp.provider.agent.AgentDetails;

public final class AgentInspectOperation implements OperationHandler<AgentInspectInput, AgentDetails> {
    @Override
    public AgentDetails execute(OperationContext context, AgentInspectInput input) {
        return context.agentProvider().inspectAgent(input.agentId());
    }
}
