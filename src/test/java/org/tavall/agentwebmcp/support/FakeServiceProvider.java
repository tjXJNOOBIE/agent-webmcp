package org.tavall.agentwebmcp.support;

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

    @Override
    public String providerName() {
        return "fake-service-provider";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<ServiceSummary> listServices() {
        return List.of(new ServiceSummary("demo.service", "Demo service", state, state == ServiceState.RUNNING ? "running" : "dead"));
    }

    @Override
    public ServiceDetails inspectService(String serviceId) {
        return details(serviceId);
    }

    @Override
    public ServiceMutationResult startService(String serviceId) {
        state = ServiceState.RUNNING;
        return mutation(serviceId, "start");
    }

    @Override
    public ServiceMutationResult stopService(String serviceId) {
        state = ServiceState.STOPPED;
        return mutation(serviceId, "stop");
    }

    @Override
    public ServiceMutationResult restartService(String serviceId) {
        state = ServiceState.RUNNING;
        return mutation(serviceId, "restart");
    }

    @Override
    public ServiceMutationResult reloadService(String serviceId) {
        return mutation(serviceId, "reload");
    }

    @Override
    public ServiceLogSlice readLogs(String serviceId, int lines, Optional<String> cursor) {
        lastAction = "logs";
        lastServiceId = serviceId;
        return new ServiceLogSlice(serviceId, "line one\nline two", "cursor-next", lines);
    }

    public String lastAction() {
        return lastAction;
    }

    public String lastServiceId() {
        return lastServiceId;
    }

    private ServiceMutationResult mutation(String serviceId, String action) {
        lastAction = action;
        lastServiceId = serviceId;
        return new ServiceMutationResult(serviceId, action, details(serviceId));
    }

    private ServiceDetails details(String serviceId) {
        return new ServiceDetails(
                serviceId,
                "Demo service",
                state,
                state == ServiceState.RUNNING ? "running" : "dead",
                state == ServiceState.RUNNING ? 42 : 0,
                1024,
                2048,
                Map.of("provider", "fake")
        );
    }
}
