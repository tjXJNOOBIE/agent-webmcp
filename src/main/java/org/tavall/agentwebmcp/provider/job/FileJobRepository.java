package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tavall.agentwebmcp.provider.ProviderException;
import org.tavall.dependency.IDependencyAccess;
import org.tavall.dependency.annotations.DelegatesTo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@DelegatesTo(JobRepository.class)
public final class FileJobRepository implements JobRepository, IDependencyAccess {
    private static final Pattern JOB_ID = Pattern.compile("job-[a-f0-9]{12}");
    private static final Pattern JOB_FILE = Pattern.compile("job-[a-f0-9]{12}\\.json");

    private final Path jobsDirectory;
    private final Object ioLock = new Object();

    private FileJobRepository(Builder builder) {
        this.jobsDirectory = Objects.requireNonNull(builder.dataDirectory, "dataDirectory").resolve("jobs");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<JobRecord> list() {
        synchronized (ioLock) {
            ensureDirectory();
            try (var files = Files.list(jobsDirectory)) {
                List<JobRecord> jobs = new ArrayList<>();
                for (Path path : files.filter(candidate -> JOB_FILE.matcher(candidate.getFileName().toString()).matches()).toList()) {
                    jobs.add(objectMapper().readValue(path.toFile(), JobRecord.class));
                }
                return List.copyOf(jobs);
            } catch (IOException exception) {
                throw repositoryFailure("Unable to list durable jobs", exception);
            }
        }
    }

    @Override
    public JobRecord read(String jobId) {
        synchronized (ioLock) {
            return readUnlocked(requireJobId(jobId));
        }
    }

    @Override
    public void write(JobRecord job) {
        Objects.requireNonNull(job, "job");
        synchronized (ioLock) {
            writeUnlocked(job);
        }
    }

    @Override
    public JobRecord update(String jobId, UnaryOperator<JobRecord> updater) {
        Objects.requireNonNull(updater, "updater");
        synchronized (ioLock) {
            JobRecord current = readUnlocked(requireJobId(jobId));
            JobRecord updated = Objects.requireNonNull(updater.apply(current), "updated job");
            if (!current.id().equals(updated.id())) {
                throw new IllegalArgumentException("job update cannot change jobId");
            }
            writeUnlocked(updated);
            return updated;
        }
    }

    private JobRecord readUnlocked(String jobId) {
        Path path = jobsDirectory.resolve(jobId + ".json");
        if (!Files.isRegularFile(path)) {
            throw new ProviderException("JOB_NOT_FOUND", "Unknown job: " + jobId, 404);
        }
        try {
            return objectMapper().readValue(path.toFile(), JobRecord.class);
        } catch (IOException exception) {
            throw repositoryFailure("Unable to read job " + jobId, exception);
        }
    }

    private void writeUnlocked(JobRecord job) {
        ensureDirectory();
        String jobId = requireJobId(job.id());
        Path target = jobsDirectory.resolve(jobId + ".json");
        Path temporary = jobsDirectory.resolve(jobId + ".tmp-" + UUID.randomUUID());
        try {
            objectMapper().writeValue(temporary.toFile(), job);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            throw repositoryFailure("Unable to persist job " + jobId, exception);
        }
    }

    private ObjectMapper objectMapper() {
        return getInstance(ObjectMapper.class);
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(jobsDirectory);
        } catch (IOException exception) {
            throw repositoryFailure("Unable to create job data directory", exception);
        }
    }

    private static String requireJobId(String jobId) {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            throw new IllegalArgumentException("jobId is invalid");
        }
        return jobId;
    }

    private static ProviderException repositoryFailure(String message, Exception exception) {
        String detail = exception.getMessage();
        return new ProviderException("JOB_REPOSITORY_FAILED", detail == null || detail.isBlank() ? message : message + ": " + detail, 500);
    }

    public static final class Builder {
        private Path dataDirectory;

        private Builder() {
        }

        public Builder dataDirectory(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
            return this;
        }

        public FileJobRepository build() {
            return new FileJobRepository(this);
        }
    }
}
