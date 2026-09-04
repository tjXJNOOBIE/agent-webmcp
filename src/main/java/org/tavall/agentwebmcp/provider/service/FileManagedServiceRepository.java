package org.tavall.agentwebmcp.provider.service;

import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.agentwebmcp.service.ServiceIdSyntax;
import org.tavall.dependency.annotations.DelegatesTo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@DelegatesTo(ManagedServiceRepository.class)
public final class FileManagedServiceRepository implements ManagedServiceRepository {
    private final Path file;
    private final Object ioLock = new Object();

    private FileManagedServiceRepository(Builder builder) {
        this.file = Objects.requireNonNull(builder.dataDirectory, "dataDirectory").resolve("managed-services.txt");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<String> list() {
        synchronized (ioLock) {
            return List.copyOf(readUnlocked());
        }
    }

    @Override
    public boolean contains(String serviceId) {
        String validServiceId = ServiceIdSyntax.require(serviceId);
        synchronized (ioLock) {
            return readUnlocked().contains(validServiceId);
        }
    }

    @Override
    public boolean add(String serviceId) {
        String validServiceId = ServiceIdSyntax.require(serviceId);
        synchronized (ioLock) {
            Set<String> services = readUnlocked();
            boolean changed = services.add(validServiceId);
            if (changed) {
                writeUnlocked(services);
            }
            return changed;
        }
    }

    @Override
    public boolean remove(String serviceId) {
        String validServiceId = ServiceIdSyntax.require(serviceId);
        synchronized (ioLock) {
            Set<String> services = readUnlocked();
            boolean changed = services.remove(validServiceId);
            if (changed) {
                writeUnlocked(services);
            }
            return changed;
        }
    }

    private Set<String> readUnlocked() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashSet<>();
        }
        try {
            Set<String> services = new LinkedHashSet<>();
            for (String line : Files.readAllLines(file)) {
                String serviceId = line.trim();
                if (!serviceId.isEmpty()) {
                    services.add(ServiceIdSyntax.require(serviceId));
                }
            }
            return services;
        } catch (IOException | IllegalArgumentException exception) {
            throw repositoryFailure("Unable to read managed services", exception);
        }
    }

    private void writeUnlocked(Set<String> services) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
            List<String> ordered = new ArrayList<>(services);
            ordered.sort(String::compareTo);
            Files.write(temporary, ordered);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw repositoryFailure("Unable to persist managed services", exception);
        }
    }

    private static ProviderException repositoryFailure(String message, Exception exception) {
        String detail = exception.getMessage();
        return new ProviderException(
                "MANAGED_SERVICE_REPOSITORY_FAILED",
                detail == null || detail.isBlank() ? message : message + ": " + detail,
                500
        );
    }

    public static final class Builder {
        private Path dataDirectory;

        private Builder() {
        }

        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
            return this;
        }

        public FileManagedServiceRepository build() {
            return new FileManagedServiceRepository(this);
        }
    }
}
