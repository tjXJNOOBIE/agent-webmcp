package org.tavall.agentwebmcp.operation;

import org.tavall.agentwebmcp.operation.handler.JobCancelOperation;
import org.tavall.agentwebmcp.operation.handler.JobExecuteOperation;
import org.tavall.agentwebmcp.operation.handler.JobInspectOperation;
import org.tavall.agentwebmcp.operation.handler.JobListOperation;
import org.tavall.agentwebmcp.operation.handler.JobLogsOperation;
import org.tavall.agentwebmcp.operation.handler.MetricsSnapshotOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceAddOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceDiagnosticsOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceDiscoverOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceInspectOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceListOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceLogsOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceReloadOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceRemoveOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceRestartOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStartOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStatusOperation;
import org.tavall.agentwebmcp.operation.handler.ServiceStopOperation;
import org.tavall.agentwebmcp.operation.handler.SystemStatusOperation;
import org.tavall.agentwebmcp.operation.handler.TargetInspectOperation;
import org.tavall.agentwebmcp.operation.handler.TargetListOperation;
import org.tavall.agentwebmcp.operation.input.EmptyInput;
import org.tavall.agentwebmcp.operation.input.JobExecuteInput;
import org.tavall.agentwebmcp.operation.input.JobIdInput;
import org.tavall.agentwebmcp.operation.input.JobListInput;
import org.tavall.agentwebmcp.operation.input.JobLogsInput;
import org.tavall.agentwebmcp.operation.input.MetricsSnapshotInput;
import org.tavall.agentwebmcp.operation.input.ServiceDiscoverInput;
import org.tavall.agentwebmcp.operation.input.ServiceIdInput;
import org.tavall.agentwebmcp.operation.input.ServiceListInput;
import org.tavall.agentwebmcp.operation.input.ServiceLogsInput;
import org.tavall.agentwebmcp.operation.input.TargetInspectInput;

public final class DefaultOperationCatalog {
    private DefaultOperationCatalog() {
    }

    public static OperationCatalog create() {
        OperationCatalog catalog = new OperationCatalog();
        register(catalog, "system.status", "Read Agent WebMCP runtime, provider, Codex runner, catalog, and authentication-mode status.", OperationAccess.READ_ONLY, EmptyInput.class, new SystemStatusOperation());
        register(catalog, "metrics.snapshot", "Read a bounded JVM and operating-system metrics snapshot for the selected target.", OperationAccess.READ_ONLY, MetricsSnapshotInput.class, new MetricsSnapshotOperation());
        register(catalog, "target.list", "List targets visible to this Agent WebMCP runtime.", OperationAccess.READ_ONLY, EmptyInput.class, new TargetListOperation());
        register(catalog, "target.inspect", "Inspect one target and its runtime capabilities.", OperationAccess.READ_ONLY, TargetInspectInput.class, new TargetInspectOperation());
        register(catalog, "service.list", "List services currently managed by Agent WebMCP on the selected target.", OperationAccess.READ_ONLY, ServiceListInput.class, new ServiceListOperation());
        register(catalog, "service.add", "Add an existing provider service to the Agent WebMCP managed-service inventory.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceAddOperation());
        register(catalog, "service.remove", "Remove a service from the Agent WebMCP managed-service inventory without deleting or stopping the provider unit.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceRemoveOperation());
        register(catalog, "service.discover", "Discover and register bounded custom/operator services, optionally using installed Codex in read-only mode for additional candidates.", OperationAccess.MUTATING, ServiceDiscoverInput.class, new ServiceDiscoverOperation());
        register(catalog, "service.inspect", "Inspect authoritative state and bounded runtime metadata for one managed service.", OperationAccess.READ_ONLY, ServiceIdInput.class, new ServiceInspectOperation());
        register(catalog, "service.status", "Read concise authoritative lifecycle status for one managed service.", OperationAccess.READ_ONLY, ServiceIdInput.class, new ServiceStatusOperation());
        register(catalog, "service.logs", "Read bounded recent managed-service logs and return a continuation cursor when available.", OperationAccess.READ_ONLY, ServiceLogsInput.class, new ServiceLogsOperation());
        register(catalog, "service.diagnostics", "Run bounded diagnostics for one managed service using provider state, PID sanity, metadata, and recent logs.", OperationAccess.READ_ONLY, ServiceIdInput.class, new ServiceDiagnosticsOperation());
        register(catalog, "service.start", "Start one managed service and return provider-observed lifecycle state.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceStartOperation());
        register(catalog, "service.stop", "Stop one managed service and return provider-observed lifecycle state.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceStopOperation());
        register(catalog, "service.restart", "Restart one managed service and return provider-observed lifecycle state.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceRestartOperation());
        register(catalog, "service.reload", "Request a provider-supported managed-service reload and return observed lifecycle state.", OperationAccess.MUTATING, ServiceIdInput.class, new ServiceReloadOperation());
        register(catalog, "job.list", "List bounded durable service jobs known to this Agent WebMCP runtime.", OperationAccess.READ_ONLY, JobListInput.class, new JobListOperation());
        register(catalog, "job.inspect", "Inspect one durable service job, schedule, evidence, and final result when available.", OperationAccess.READ_ONLY, JobIdInput.class, new JobInspectOperation());
        register(catalog, "job.logs", "Read bounded job lifecycle logs using a continuation cursor.", OperationAccess.READ_ONLY, JobLogsInput.class, new JobLogsOperation());
        register(catalog, "job.execute", "Create a bounded managed-service job: an explicit deterministic service operation when prompt is empty, or a one-shot installed-Codex job when prompt is present.", OperationAccess.MUTATING, JobExecuteInput.class, new JobExecuteOperation());
        register(catalog, "job.cancel", "Cancel a queued or scheduled durable service job; refuse running cancellation unless process ownership can be proven safe.", OperationAccess.MUTATING, JobIdInput.class, new JobCancelOperation());
        return catalog;
    }

    private static <I, R> void register(
            OperationCatalog catalog,
            String id,
            String description,
            OperationAccess access,
            Class<I> inputType,
            OperationHandler<I, R> handler
    ) {
        catalog.register(new OperationRegistration<>(
                new OperationDescriptor(OperationId.of(id), description, access),
                inputType,
                handler
        ));
    }
}
