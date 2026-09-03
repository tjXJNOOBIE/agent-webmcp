package org.tavall.agentwebmcp.support;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceLogSlice;
import org.tavall.agentwebmcp.provider.service.ServiceMutationResult;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.agentwebmcp.provider.service.ServiceState;
import org.tavall.agentwebmcp.provider.service.ServiceSummary;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FakeServiceProvider implements ServiceProvider {
    private ServiceState state = ServiceState.RUNNING;
    private String lastAction = "";
    private String lastServiceId = "";
    private boolean missing;
    private boolean failLogs;
    private long pid = 42;
    private long lifecycleDelayMillis;
    private String unitFileState = "enabled";

    @Override
    public String providerName() { return "fake-service-provider"; }

    @Override
    public boolean available() { return true; }

    @Override
    public List<ServiceSummary> listServices() {
        if (missing) return List.of();
        return List.of(
                summary("demo.service", "Demo service"),
                summary("opt-worker.service", "Operator worker"),
                summary("vendor.service", "Vendor service"),
                summary("systemd-journald.service", "System journal")
        );
    }

    @Override
    public ServiceDetails inspectService(String serviceId) {
        if (missing || !known(serviceId)) {
            throw new ProviderException("SERVICE_NOT_FOUND", "Unknown service: " + serviceId, 404);
        }
        return details(serviceId);
    }

    @Override
    public ServiceMutationResult startService(String serviceId) { state = ServiceState.RUNNING; return mutation(serviceId, "start"); }

    @Override
    public ServiceMutationResult stopService(String serviceId) { state = ServiceState.STOPPED; return mutation(serviceId, "stop"); }

    @Override
    public ServiceMutationResult restartService(String serviceId) { state = ServiceState.RUNNING; return mutation(serviceId, "restart"); }

    @Override
    public ServiceMutationResult reloadService(String serviceId) { return mutation(serviceId, "reload"); }

    @Override
    public ServiceLogSlice readLogs(String serviceId, int lines, Optional<String> cursor) {
        inspectService(serviceId);
        if (failLogs) throw new ProviderException("SERVICE_LOGS_FAILED", "Synthetic log failure", 502);
        lastAction = "logs";
        lastServiceId = serviceId;
        return new ServiceLogSlice(serviceId, "line one\nline two", "cursor-next", lines);
    }

    public String lastAction() { return lastAction; }
    public String lastServiceId() { return lastServiceId; }
    public void setMissing(boolean missing) { this.missing = missing; }
    public void setFailLogs(boolean failLogs) { this.failLogs = failLogs; }
    public void setState(ServiceState state) { this.state = state; }
    public void setPid(long pid) { this.pid = pid; }
    public void setLifecycleDelayMillis(long lifecycleDelayMillis) { this.lifecycleDelayMillis = lifecycleDelayMillis; }
    public void setUnitFileState(String unitFileState) { this.unitFileState = unitFileState; }

    private ServiceMutationResult mutation(String serviceId, String action) {
        inspectService(serviceId);
        delay();
        lastAction = action;
        lastServiceId = serviceId;
        return new ServiceMutationResult(serviceId, action, details(serviceId));
    }

    private void delay() {
        if (lifecycleDelayMillis <= 0) return;
        try {
            Thread.sleep(lifecycleDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderException("SERVICE_OPERATION_INTERRUPTED", "Synthetic service operation interrupted", 500);
        }
    }

    private ServiceSummary summary(String id, String description) {
        return new ServiceSummary(id, description, state, state == ServiceState.RUNNING ? "running" : "dead");
    }

    private ServiceDetails details(String serviceId) {
        long observedPid = state == ServiceState.RUNNING ? pid : 0;
        return new ServiceDetails(
                serviceId, description(serviceId), state, state == ServiceState.RUNNING ? "running" : "dead",
                observedPid, 1024, 2048, Map.of(
                        "provider", "fake",
                        "FragmentPath", fragmentPath(serviceId),
                        "WorkingDirectory", "/tmp",
                        "UnitFileState", unitFileState
                )
        );
    }

    private static boolean known(String serviceId) {
        return serviceId.equals("demo.service") || serviceId.equals("opt-worker.service")
                || serviceId.equals("vendor.service") || serviceId.equals("systemd-journald.service");
    }

    private static String description(String serviceId) {
        return switch (serviceId) {
            case "demo.service" -> "Demo service";
            case "opt-worker.service" -> "Operator worker";
            case "vendor.service" -> "Vendor service";
            case "systemd-journald.service" -> "System journal";
            default -> serviceId;
        };
    }

    private static String fragmentPath(String serviceId) {
        return switch (serviceId) {
            case "demo.service" -> "/etc/systemd/system/demo.service";
            case "opt-worker.service" -> "/opt/agent-worker/opt-worker.service";
            case "vendor.service" -> "/usr/lib/systemd/system/vendor.service";
            case "systemd-journald.service" -> "/usr/lib/systemd/system/systemd-journald.service";
            default -> "";
        };
    }
}
