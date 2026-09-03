package org.tavall.agentwebmcp.provider.target;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.dependency.annotations.DelegatesTo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

@DelegatesTo(TargetProvider.class)
public final class LocalTargetProvider implements TargetProvider {
    public static final String LOCAL_TARGET_ID = "local";

    @Override
    public String providerName() {
        return "local-jvm";
    }

    @Override
    public List<TargetSummary> listTargets() {
        return List.of(new TargetSummary(
                LOCAL_TARGET_ID,
                hostname(),
                System.getProperty("os.name"),
                System.getProperty("os.arch")
        ));
    }

    @Override
    public TargetDetails inspectTarget(String targetId) {
        requireLocal(targetId);
        return new TargetDetails(
                LOCAL_TARGET_ID,
                hostname(),
                hostname(),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"),
                Map.of("local", true)
        );
    }

    public static void requireLocal(String targetId) {
        if (!LOCAL_TARGET_ID.equals(targetId)) {
            throw new ProviderException("TARGET_NOT_FOUND", "Unknown target: " + targetId, 404);
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "localhost";
        }
    }
}
