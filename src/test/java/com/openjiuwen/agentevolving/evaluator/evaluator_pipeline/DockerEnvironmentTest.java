/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's evaluator-pipeline docker environment tests in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_docker_env.py}.
 */
class DockerEnvironmentTest {

    @Test
    void testDefaultValues() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");

        assertEquals("test-image", environment.getImageTag());
        assertEquals(1, environment.getCpus());
        assertEquals(2048, environment.getMemoryMb());
        assertEquals(900, environment.getTimeout());
        assertNull(environment.getContainerId());
    }

    @Test
    void testCustomValues() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image", "test-container", 4, 4096, 1800);

        assertEquals(4, environment.getCpus());
        assertEquals(4096, environment.getMemoryMb());
        assertEquals(1800, environment.getTimeout());
        assertEquals("test-container", environment.getContainerName());
    }

    @Test
    void testIsRunningProperty() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        assertFalse(environment.isRunning());

        environment.setContainerIdForTesting("abc123");
        assertTrue(environment.isRunning());
    }

    @Test
    void testContainerNameProperty() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test/image:tag");
        assertEquals("test_image_tag", environment.getContainerName());

        FakeDockerEnvironment second = new FakeDockerEnvironment("simple", "custom-name", 1, 2048, 900);
        assertEquals("custom-name", second.getContainerName());
    }

    @Test
    void testBuildWithMissingDockerfile(@TempDir Path tempDir) {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        Path missingPath = tempDir.resolve("nonexistent").resolve("Dockerfile");

        UncheckedIOException error = assertThrows(UncheckedIOException.class, () -> environment.build(missingPath, tempDir));
        assertTrue(error.getCause() instanceof java.io.FileNotFoundException);
    }

    @Test
    void testBuildSuccess(@TempDir Path tempDir) throws Exception {
        Path dockerfile = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfile, "FROM alpine");

        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.queueResult(new ExecResult("", "", 0, false));

        String result = environment.build(dockerfile, tempDir);

        assertEquals("test-image", result);
        assertEquals(1, environment.getCommandHistory().size());
    }

    @Test
    void testBuildFailure(@TempDir Path tempDir) throws Exception {
        Path dockerfile = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfile, "FROM alpine");

        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.queueResult(new ExecResult("", "build failed", 1, false));

        RuntimeException error = assertThrows(RuntimeException.class, () -> environment.build(dockerfile, tempDir));
        assertTrue(error.getMessage().contains("docker build failed"));
    }

    @Test
    void testBuildTimeout(@TempDir Path tempDir) throws Exception {
        Path dockerfile = tempDir.resolve("Dockerfile");
        Files.writeString(dockerfile, "FROM alpine");

        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.queueResult(new ExecResult("", "", -1, true));

        RuntimeException error = assertThrows(RuntimeException.class, () -> environment.build(dockerfile, tempDir));
        assertTrue(error.getMessage().contains("timed out"));
    }

    @Test
    void testStartSuccess() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.queueResult(new ExecResult("container-id-12345", "", 0, false));

        environment.start().join();

        assertEquals("container-id-12345", environment.getContainerId());
        assertEquals(1, environment.getCommandHistory().size());
    }

    @Test
    void testStartFailure() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.queueResult(new ExecResult("", "failed to start", 1, false));

        RuntimeException error = assertThrows(RuntimeException.class, () -> environment.start().join());
        assertTrue(error.getMessage().contains("Failed to start container"));
    }

    @Test
    void testStopWhenNotRunning() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.stop().join();

        assertEquals(0, environment.getCommandHistory().size());
    }

    @Test
    void testStopSuccess() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id-12345");
        environment.queueResult(new ExecResult("", "", 0, false));
        environment.queueResult(new ExecResult("", "", 0, false));

        environment.stop().join();

        assertNull(environment.getContainerId());
        assertEquals(2, environment.getCommandHistory().size());
    }

    @Test
    void testExecNoContainer() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");

        ExecResult result = environment.exec("echo hello").join();

        assertEquals(-1, result.getReturncode());
        assertTrue(result.getStderr().contains("No container running"));
    }

    @Test
    void testExecSuccess() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id");
        environment.queueResult(new ExecResult("hello world", "", 0, false));

        ExecResult result = environment.exec("echo hello world").join();

        assertTrue(result.isSuccess());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void testExecWithWorkdir() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id");
        environment.queueResult(new ExecResult("result", "", 0, false));

        environment.exec("pwd", 300, "/app", null).join();

        List<String> command = environment.getLastCommand();
        assertTrue(command.contains("-w"));
        assertTrue(command.contains("/app"));
    }

    @Test
    void testExecWithEnv() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id");
        environment.queueResult(new ExecResult("result", "", 0, false));

        Map<String, String> env = new LinkedHashMap<>();
        env.put("VAR1", "value1");
        env.put("VAR2", "value2");
        environment.exec("env", 300, null, env).join();

        List<String> command = environment.getLastCommand();
        assertTrue(command.contains("-e"));
        assertTrue(command.contains("VAR1=value1"));
    }

    @Test
    void testCopyToNoContainer() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        boolean result = environment.copyTo(Path.of("/tmp/file"), "/dest/path").join();
        assertFalse(result);
    }

    @Test
    void testCopyToSuccess() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id");
        environment.queueResult(new ExecResult("", "", 0, false));

        boolean result = environment.copyTo(Path.of("/tmp/file"), "/dest/path").join();

        assertTrue(result);
    }

    @Test
    void testCopyFromNoContainer() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        boolean result = environment.copyFrom("/src/path", Path.of("/tmp/file")).join();
        assertFalse(result);
    }

    @Test
    void testCopyFromSuccess() {
        FakeDockerEnvironment environment = new FakeDockerEnvironment("test-image");
        environment.setContainerIdForTesting("container-id");
        environment.queueResult(new ExecResult("", "", 0, false));

        boolean result = environment.copyFrom("/src/path", Path.of("/tmp/file")).join();

        assertTrue(result);
    }

    @Test
    void testRunCommandException() {
        ExplodingDockerEnvironment environment = new ExplodingDockerEnvironment("test-image");

        ExecResult result = environment.runCommand(List.of("nonexistent_command_xyz_123"), 300).join();

        assertFalse(result.isSuccess());
        assertEquals(-1, result.getReturncode());
    }

    private static final class FakeDockerEnvironment extends DockerEnvironment {

        private final List<ExecResult> queuedResults = new java.util.ArrayList<>();
        private final List<List<String>> commandHistory = new java.util.ArrayList<>();

        private FakeDockerEnvironment(String imageTag) {
            super(imageTag);
        }

        private FakeDockerEnvironment(String imageTag, String containerName, int cpus, int memoryMb, int timeout) {
            super(imageTag, containerName, cpus, memoryMb, timeout);
        }

        @Override
        protected String resolveDockerPath() {
            return "/usr/bin/docker";
        }

        @Override
        protected CompletableFuture<ExecResult> runCommand(List<String> command, int commandTimeout) {
            commandHistory.add(List.copyOf(command));
            if (queuedResults.isEmpty()) {
                return CompletableFuture.completedFuture(new ExecResult("", "", 0, false));
            }
            return CompletableFuture.completedFuture(queuedResults.remove(0));
        }

        @Override
        protected CompletableFuture<Void> delay(long millis) {
            return CompletableFuture.completedFuture(null);
        }

        private void queueResult(ExecResult result) {
            queuedResults.add(result);
        }

        private List<List<String>> getCommandHistory() {
            return commandHistory;
        }

        private List<String> getLastCommand() {
            return commandHistory.get(commandHistory.size() - 1);
        }
    }

    private static final class ExplodingDockerEnvironment extends DockerEnvironment {

        private ExplodingDockerEnvironment(String imageTag) {
            super(imageTag);
        }

        @Override
        protected String resolveDockerPath() {
            return "/usr/bin/docker";
        }

        @Override
        protected CompletableFuture<ExecResult> runCommand(List<String> command, int commandTimeout) {
            return CompletableFuture.supplyAsync(() -> {
                throw new IllegalStateException("boom");
            }).handle((ignored, error) -> new ExecResult(
                    "",
                    error == null ? "" : error.getMessage(),
                    -1,
                    false));
        }
    }
}
