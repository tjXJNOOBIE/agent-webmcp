package org.tavall.agentwebmcp.provider.service;

import java.util.List;

public interface ManagedServiceRepository {
    List<String> list();

    boolean contains(String serviceId);

    boolean add(String serviceId);

    boolean remove(String serviceId);
}
