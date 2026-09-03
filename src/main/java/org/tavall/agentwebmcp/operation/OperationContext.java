package org.tavall.agentwebmcp.operation;

import org.tavall.agentwebmcp.AuthMode;
import org.tavall.agentwebmcp.provider.job.JobProvider;
import org.tavall.agentwebmcp.provider.metrics.MetricsProvider;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.agentwebmcp.provider.target.TargetProvider;

import java.util.Objects;

public record OperationContext(
        String runtimeVersion,
        AuthMode authMode,
        OperationCatalog catalog,
        TargetProvider targetProvider,
        ServiceProvider serviceProvider,
        MetricsProvider metricsProvider,
        JobProvider jobProvider,
        OperationInvoker operationInvoker
) {
    public OperationContext {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        Objects.requireNonNull(authMode, "authMode");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(targetProvider, "targetProvider");
        Objects.requireNonNull(serviceProvider, "serviceProvider");
        Objects.requireNonNull(metricsProvider, "metricsProvider");
        Objects.requireNonNull(jobProvider, "jobProvider");
        Objects.requireNonNull(operationInvoker, "operationInvoker");
    }

    public int operationCount() {
        return catalog.size();
    }
}
