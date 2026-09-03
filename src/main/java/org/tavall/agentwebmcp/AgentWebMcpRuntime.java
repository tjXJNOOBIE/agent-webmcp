package org.tavall.agentwebmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.agentwebmcp.operation.DefaultOperationCatalog;
import org.tavall.agentwebmcp.operation.DeferredOperationInvoker;
import org.tavall.agentwebmcp.operation.OperationCatalog;
import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationExecutor;
import org.tavall.agentwebmcp.operation.OperationInvoker;
import org.tavall.agentwebmcp.provider.job.FileJobRepository;
import org.tavall.agentwebmcp.provider.job.JobProvider;
import org.tavall.agentwebmcp.provider.job.JobRepository;
import org.tavall.agentwebmcp.provider.job.LocalJobProvider;
import org.tavall.agentwebmcp.provider.metrics.JvmSystemMetricsProvider;
import org.tavall.agentwebmcp.provider.metrics.MetricsProvider;
import org.tavall.agentwebmcp.provider.process.CommandExecutor;
import org.tavall.agentwebmcp.provider.process.ProcessCommandExecutor;
import org.tavall.agentwebmcp.provider.service.FileManagedServiceRepository;
import org.tavall.agentwebmcp.provider.service.ManagedServiceRepository;
import org.tavall.agentwebmcp.provider.service.ServiceProvider;
import org.tavall.agentwebmcp.provider.service.SystemdServiceProvider;
import org.tavall.agentwebmcp.provider.target.LocalTargetProvider;
import org.tavall.agentwebmcp.provider.target.TargetProvider;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;
import org.tavall.dependency.maps.DependencyMap;
import org.tavall.dependency.maps.interfaces.IDependencyMap;

import java.nio.file.Path;
import java.util.Objects;

@DelegatesTo
public final class AgentWebMcpRuntime implements IDependencyAccess {
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private AgentWebMcpRuntime() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentWebMcpRuntime createDefault() {
        return builder().build();
    }

    public IDependencyMap dependencyMap() {
        return getDependencyMap();
    }

    public ObjectMapper objectMapper() {
        return getInstance(ObjectMapper.class);
    }

    public OperationCatalog catalog() {
        return getInstance(OperationCatalog.class);
    }

    public OperationContext context() {
        return getInstance(OperationContext.class);
    }

    public OperationExecutor executor() {
        return getInstance(OperationExecutor.class);
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
            DependencyMap dependencies = DependencyMap.getDependencyMap();

            ObjectMapper resolvedObjectMapper = objectMapper == null
                    ? new ObjectMapper().findAndRegisterModules()
                    : objectMapper;
            dependencies.registerInstance(ObjectMapper.class, resolvedObjectMapper);

            OperationCatalog resolvedCatalog = catalog == null ? DefaultOperationCatalog.create() : catalog;
            dependencies.registerInstance(OperationCatalog.class, resolvedCatalog);

            dependencies.registerInstance(CommandExecutor.class, new ProcessCommandExecutor());

            TargetProvider resolvedTargetProvider = targetProvider == null ? new LocalTargetProvider() : targetProvider;
            dependencies.registerInstance(TargetProvider.class, resolvedTargetProvider);

            Path resolvedDataDirectory = dataDirectory == null ? defaultDataDirectory() : dataDirectory;
            ManagedServiceRepository managedServiceRepository = FileManagedServiceRepository.builder()
                    .dataDirectory(resolvedDataDirectory)
                    .build();
            dependencies.registerInstance(ManagedServiceRepository.class, managedServiceRepository);

            ServiceProvider resolvedServiceProvider = serviceProvider == null
                    ? SystemdServiceProvider.builder().build()
                    : serviceProvider;
            dependencies.registerInstance(ServiceProvider.class, resolvedServiceProvider);

            MetricsProvider resolvedMetricsProvider = metricsProvider == null
                    ? new JvmSystemMetricsProvider()
                    : metricsProvider;
            dependencies.registerInstance(MetricsProvider.class, resolvedMetricsProvider);

            JobProvider resolvedJobProvider = jobProvider;
            if (resolvedJobProvider == null) {
                JobRepository repository = FileJobRepository.builder()
                        .dataDirectory(resolvedDataDirectory)
                        .build();
                dependencies.registerInstance(JobRepository.class, repository);
                resolvedJobProvider = new LocalJobProvider();
            }
            dependencies.registerInstance(JobProvider.class, resolvedJobProvider);

            DeferredOperationInvoker operationInvoker = new DeferredOperationInvoker();
            dependencies.registerInstance(OperationInvoker.class, operationInvoker);

            OperationContext context = new OperationContext(
                    VERSION,
                    AuthMode.NO_AUTH,
                    dependencies.getInstance(OperationCatalog.class),
                    dependencies.getInstance(TargetProvider.class),
                    dependencies.getInstance(ServiceProvider.class),
                    dependencies.getInstance(ManagedServiceRepository.class),
                    dependencies.getInstance(MetricsProvider.class),
                    dependencies.getInstance(JobProvider.class),
                    dependencies.getInstance(OperationInvoker.class)
            );
            dependencies.registerInstance(OperationContext.class, context);

            OperationExecutor executor = new OperationExecutor();
            dependencies.registerInstance(OperationExecutor.class, executor);
            operationInvoker.bind(executor::execute);

            AgentWebMcpRuntime runtime = new AgentWebMcpRuntime();
            dependencies.registerInstance(AgentWebMcpRuntime.class, runtime);
            return runtime;
        }
    }
}
