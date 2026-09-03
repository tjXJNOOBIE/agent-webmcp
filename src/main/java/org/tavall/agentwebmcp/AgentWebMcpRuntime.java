package org.tavall.agentwebmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.agentwebmcp.operation.DefaultOperationCatalog;
import org.tavall.agentwebmcp.operation.DeferredOperationInvoker;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationExecutor;
import org.tavall.agentwebmcp.provider.job.JobProvider;
import org.tavall.agentwebmcp.provider.job.LocalJobProvider;
import org.tavall.agentwebmcp.provider.metrics.JvmSystemMetricsProvider;
import org.tavall.agentwebmcp.provider.metrics.MetricsProvider;
import org.tavall.agentwebmcp.provider.process.ProcessCommandExecutor;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.agentwebmcp.provider.service.SystemdServiceProvider;
import org.tavall.agentwebmcp.provider.target.LocalTargetProvider;
import org.tavall.agentwebmcp.provider.target.TargetProvider;

import java.nio.file.Path;
import java.util.Objects;

public final class AgentWebMcpRuntime {
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private final ObjectMapper objectMapper;
    private final OperationCatalog catalog;
    private final OperationContext context;
    private final OperationExecutor executor;

    private AgentWebMcpRuntime(Builder builder) {
        this.objectMapper = builder.objectMapper == null
                ? new ObjectMapper().findAndRegisterModules()
                : builder.objectMapper;
        this.catalog = builder.catalog == null ? DefaultOperationCatalog.create() : builder.catalog;

        TargetProvider targetProvider = builder.targetProvider == null ? new LocalTargetProvider() : builder.targetProvider;
        ServiceProvider serviceProvider = builder.serviceProvider == null
                ? SystemdServiceProvider.builder().commandExecutor(new ProcessCommandExecutor()).build()
                : builder.serviceProvider;
        MetricsProvider metricsProvider = builder.metricsProvider == null
                ? new JvmSystemMetricsProvider()
                : builder.metricsProvider;
        JobProvider jobProvider = builder.jobProvider == null
                ? LocalJobProvider.builder()
                        .objectMapper(objectMapper)
                        .dataDirectory(builder.dataDirectory == null ? defaultDataDirectory() : builder.dataDirectory)
                        .build()
                : builder.jobProvider;

        DeferredOperationInvoker operationInvoker = new DeferredOperationInvoker();
        this.context = new OperationContext(
                VERSION,
                AuthMode.NO_AUTH,
                catalog,
                targetProvider,
                serviceProvider,
                metricsProvider,
                jobProvider,
                operationInvoker
        );
        this.executor = OperationExecutor.builder()
                .catalog(catalog)
                .objectMapper(objectMapper)
                .context(context)
                .build();
        operationInvoker.bind(executor::execute);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentWebMcpRuntime createDefault() {
        return builder().build();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public OperationCatalog catalog() {
        return catalog;
    }

    public OperationContext context() {
        return context;
    }

    public OperationExecutor executor() {
        return executor;
    }

    private static Path defaultDataDirectory() {
        String configured = System.getenv("AGENT_WEBMCP_DATA_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".agent-webmcp");
    }

    public static final class Builder {
        private ObjectMapper objectMapper;
        private OperationCatalog catalog;
        private TargetProvider targetProvider;
        private ServiceProvider serviceProvider;
        private MetricsProvider metricsProvider;
        private JobProvider jobProvider;
        private Path dataDirectory;

        private Builder() {
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            return this;
        }

        public Builder catalog(OperationCatalog catalog) {
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            return this;
        }

        public Builder targetProvider(TargetProvider targetProvider) {
            this.targetProvider = Objects.requireNonNull(targetProvider, "targetProvider");
            return this;
        }

        public Builder serviceProvider(ServiceProvider serviceProvider) {
            this.serviceProvider = Objects.requireNonNull(serviceProvider, "serviceProvider");
            return this;
        }

        public Builder metricsProvider(MetricsProvider metricsProvider) {
            this.metricsProvider = Objects.requireNonNull(metricsProvider, "metricsProvider");
            return this;
        }

        public Builder jobProvider(JobProvider jobProvider) {
            this.jobProvider = Objects.requireNonNull(jobProvider, "jobProvider");
            return this;
        }

        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
            return this;
        }

        public AgentWebMcpRuntime build() {
            return new AgentWebMcpRuntime(this);
        }
    }
}
