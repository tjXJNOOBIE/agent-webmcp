package org.tavall.agentwebmcp.operation.handler;

import org.tavall.agentwebmcp.provider.service.ServiceDetails;

import java.nio.file.Path;
import java.util.Set;

final class ServiceDiscoveryPolicy {
    private static final Set<String> DENIED_IDS = Set.of(
            "dbus.service",
            "systemd-journald.service",
            "systemd-logind.service",
            "systemd-udevd.service",
            "systemd-resolved.service",
            "systemd-timesyncd.service"
    );

    private ServiceDiscoveryPolicy() {
    }

    static boolean shouldAutoRegister(ServiceDetails service) {
        if (DENIED_IDS.contains(service.id())
                || service.id().startsWith("user@")
                || service.id().startsWith("systemd-")
                || service.id().startsWith("getty@")
                || service.id().startsWith("serial-getty@")) {
            return false;
        }
        String fragment = service.providerMetadata().getOrDefault("FragmentPath", "").trim();
        if (fragment.isEmpty()) {
            return false;
        }
        try {
            Path path = Path.of(fragment).normalize();
            return path.startsWith("/etc/systemd/system")
                    || path.startsWith("/usr/local/lib/systemd/system")
                    || path.startsWith("/opt");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
