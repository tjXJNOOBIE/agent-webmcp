package org.tavall.agentwebmcp.provider.job;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tavall.agentwebmcp.AgentWebMcpRuntime;
import org.tavall.agentwebmcp.support.FakeCodexCliProvider;
import org.tavall.agentwebmcp.support.FakeServiceProvider;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileJobRepositorySecurityTest {
    @TempDir Path dataDirectory;

    @Test
    void durableJobDirectoryAndRecordsAreOwnerOnly() throws Exception {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        try (AgentWebMcpRuntime runtime = AgentWebMcpRuntime.builder()
                .serviceProvider(new FakeServiceProvider())
                .codexCliProvider(new FakeCodexCliProvider())
                .dataDirectory(dataDirectory)
                .build()) {
            JobSubmission submission = runtime.context().jobProvider().submit(new JobRequest(
                    "local", "demo.service", JobKind.SERVICE_OPERATION, "service.restart",
                    JsonNodeFactory.instance.objectNode(), Optional.empty(), Optional.of(Instant.now().plusSeconds(60)),
                    Optional.empty(), 5, Optional.empty()));

            Path jobsDirectory = dataDirectory.resolve("jobs");
            Path jobFile = jobsDirectory.resolve(submission.jobId() + ".json");
            assertEquals(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ), Files.getPosixFilePermissions(jobsDirectory));
            assertEquals(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ), Files.getPosixFilePermissions(jobFile));
        }
    }
}
