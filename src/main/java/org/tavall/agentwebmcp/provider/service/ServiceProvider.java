package org.tavall.agentwebmcp.provider.service;

import java.util.List;
import java.util.Optional;

public interface ServiceProvider {
    String providerName();

    boolean available();

    List<ServiceSummary> listServices();

    ServiceDetails inspectService(String serviceId);

    ServiceMutationResult startService(String serviceId);

    ServiceMutationResult stopService(String serviceId);

    ServiceMutationResult restartService(String serviceId);

    ServiceMutationResult reloadService(String serviceId);

    ServiceLogSlice readLogs(String serviceId, int lines, Optional<String> cursor);
}
