package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.operation.OperationContext;
import org.tavall.agentwebmcp.operation.OperationHandler;
import org.tavall.agentwebmcp.operation.input.ServiceDiscoverInput;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.provider.service.ServiceDetails;
import org.tavall.agentwebmcp.provider.service.ServiceSummary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ServiceDiscoverOperation implements OperationHandler<ServiceDiscoverInput, ServiceDiscoveryResult> {
    @Override
    public ServiceDiscoveryResult execute(OperationContext context, ServiceDiscoverInput input) {
        OperationTargets.resolve(context, input.targetId());
        LinkedHashSet<String> deterministicCandidates = new LinkedHashSet<>();
        LinkedHashSet<String> skipped = new LinkedHashSet<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();

        for (ServiceSummary summary : context.serviceProvider().listServices()) {
            try {
                ServiceDetails details = context.serviceProvider().inspectService(summary.id());
                if (ServiceDiscoveryPolicy.shouldAutoRegister(details)) {
                    deterministicCandidates.add(details.id());
                } else {
                    skipped.add(details.id());
                }
            } catch (ProviderException exception) {
                rejected.add(summary.id());
            }
        }

        LinkedHashSet<String> aiCandidates = new LinkedHashSet<>();
        if (input.includeAi()) {
            for (String serviceId : context.codexCliProvider().discoverServiceIds(Duration.ofSeconds(90))) {
                try {
                    aiCandidates.add(context.serviceProvider().inspectService(serviceId).id());
                } catch (ProviderException exception) {
                    rejected.add(serviceId);
                }
            }
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>(deterministicCandidates);
        requested.addAll(aiCandidates);
        List<String> registered = new ArrayList<>();
        List<String> alreadyManaged = new ArrayList<>();
        for (String serviceId : requested) {
            if (context.managedServiceRepository().add(serviceId)) {
                registered.add(serviceId);
            } else {
                alreadyManaged.add(serviceId);
            }
        }

        return new ServiceDiscoveryResult(
                List.copyOf(deterministicCandidates),
                List.copyOf(aiCandidates),
                List.copyOf(registered),
                List.copyOf(alreadyManaged),
                List.copyOf(skipped),
                List.copyOf(rejected),
                context.serviceProvider().providerName(),
                input.includeAi()
        );
    }
}
