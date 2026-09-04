package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceLogSlice;
import org.tavall.agentwebmcp.provider.service.ServiceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ServiceDiagnosticsOperation implements OperationHandler<ServiceIdInput, ServiceDiagnosticsOperation.Result> {
    @Override
    public Result execute(OperationContext context, ServiceIdInput input) {
        OperationTargets.resolve(context, input.targetId());
        ManagedServiceAccess.requireManaged(context, input.serviceId());
        ServiceDetails details = context.serviceProvider().inspectService(input.serviceId());
        List<String> findings = new ArrayList<>();

        if (details.state() != ServiceState.RUNNING) {
            findings.add("Lifecycle state is " + details.state() + " / " + details.subState());
        }
        if (details.state() == ServiceState.RUNNING && details.pid() <= 0) {
            findings.add("Service reports RUNNING without a positive main PID");
        }
        String unitFileState = details.providerMetadata().getOrDefault("UnitFileState", "").trim();
        if ("masked".equals(unitFileState)) {
            findings.add("Unit file is masked");
        }

        ServiceLogSlice logs;
        try {
            logs = context.serviceProvider().readLogs(input.serviceId(), 80, Optional.empty());
        } catch (ProviderException exception) {
            findings.add("Recent logs unavailable: " + exception.code());
            logs = new ServiceLogSlice(input.serviceId(), "", "", 80);
        }
        boolean healthy = findings.isEmpty();
        return new Result(details, logs, healthy, List.copyOf(findings));
    }

    public record Result(
            ServiceDetails service,
            ServiceLogSlice recentLogs,
            boolean healthy,
            List<String> findings
    ) {
    }
}
