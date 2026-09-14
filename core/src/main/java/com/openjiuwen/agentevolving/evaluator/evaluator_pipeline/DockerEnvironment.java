/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Docker-backed execution environment for evaluator pipeline tasks.
 *
 * <p>Mirrors Python's {@code DockerEnvironment} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/docker_env.py}.</p>
 */
@Getter
public class DockerEnvironment {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private final String imageTag;
    private final String containerNameOverride;
    private final int cpus;
    private final int memoryMb;
    private final int timeout;

    private volatile String containerId;

    public DockerEnvironment(String imageTag) {
        this(imageTag, null, 1, 2048, 900);
    }

    public DockerEnvironment(String imageTag, String containerName, int cpus, int memoryMb, int timeout) {
        this.imageTag = imageTag;
        this.containerNameOverride = containerName;
        this.cpus = cpus;
        this.memoryMb = memoryMb;
        this.timeout = timeout;
    }

    public boolean isRunning() {
        return containerId != null;
    }

    public String getContainerName() {
        if (containerNameOverride != null && !containerNameOverride.isBlank()) {
            return containerNameOverride;
        }
        return imageTag.replace("/", "_").replace(":", "_");
    }

    public String build(Path dockerfilePath, Path buildContext) {
        return build(dockerfilePath, buildContext, 600, false, null);
    }

    public String build(
            Path dockerfilePath,
            Path buildContext,
            int buildTimeout,
            boolean noCache,
            Map<String, String> buildArgs) {
        if (!Files.exists(dockerfilePath)) {
            throw new UncheckedIOException(new FileNotFoundException("Dockerfile not found: " + dockerfilePath));
        }

        List<String> command = new ArrayList<>();
        command.add(resolveDockerPath());
        command.add("build");
        if (noCache) {
            command.add("--no-cache");
        }
        if (buildArgs != null) {
            for (Map.Entry<String, String> entry : buildArgs.entrySet()) {
                command.add("--build-arg");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        command.add("-t");
        command.add(imageTag);
        command.add("-f");
        command.add(dockerfilePath.toString());
        command.add(buildContext.toString());

        ExecResult result = runCommand(command, buildTimeout).join();
        if (result.isTimedOut()) {
            throw new RuntimeException("docker build timed out after " + buildTimeout + "s");
        }
        if (result.getReturncode() != 0) {
            LOGGER.error("=== Docker Build Error Details ===");
            LOGGER.error("Command: {}", String.join(" ", command));
            LOGGER.error("stdout:\n{}", result.getStdout());
            LOGGER.error("stderr:\n{}", result.getStderr());
            LOGGER.error("==================================");
            throw new RuntimeException(
                    "docker build failed (rc=" + result.getReturncode() + "): "
                            + truncate(result.getStderr(), 2000));
        }

        LOGGER.info("Image built: {}", imageTag);
        return imageTag;
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> initial = isRunning() ? stop() : CompletableFuture.completedFuture(null);
        return initial.thenCompose(ignored -> {
            List<String> command = List.of(
                    resolveDockerPath(),
                    "run",
                    "-d",
                    "--memory",
                    memoryMb + "m",
                    "--cpus",
                    String.valueOf(cpus),
                    imageTag,
                    "tail",
                    "-f",
                    "/dev/null");
            return runCommand(command, 60).thenCompose(result -> {
                if (result.getReturncode() != 0) {
                    throw new RuntimeException("Failed to start container: " + result.getStderr());
                }
                containerId = result.getStdout().strip();
                return delay(2000L).thenRun(() -> {
                    if (containerId != null && containerId.length() >= 12) {
                        LOGGER.info("Container started: {}", containerId.substring(0, 12));
                    } else {
                        LOGGER.info("Container started: {}", containerId);
                    }
                });
            });
        });
    }

    public CompletableFuture<Void> stop() {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(null);
        }
        String currentContainerId = containerId;
        String shortId = shortenContainerId(currentContainerId);
        LOGGER.info("Stopping container: {}", shortId);
        return runCommand(List.of(resolveDockerPath(), "stop", currentContainerId), 30)
                .handle((ignored, stopError) -> null)
                .thenCompose(ignored -> runCommand(List.of(resolveDockerPath(), "rm", currentContainerId), 30)
                        .handle((ignoredResult, rmError) -> {
                            if (rmError != null) {
                                Throwable cause = unwrap(rmError);
                                LOGGER.error("Error stopping container: {}", cause.getMessage());
                            }
                            return null;
                        }))
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    LOGGER.error("Error stopping container: {}", cause.getMessage());
                    return null;
                })
                .thenRun(() -> containerId = null);
    }

    public CompletableFuture<ExecResult> exec(String command) {
        return exec(command, 300, null, null);
    }

    public CompletableFuture<ExecResult> exec(
            String command,
            int commandTimeout,
            String workdir,
            Map<String, String> env) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(new ExecResult("", "No container running", -1, false));
        }

        List<String> argv = new ArrayList<>();
        argv.add(resolveDockerPath());
        argv.add("exec");
        if (workdir != null && !workdir.isBlank()) {
            argv.add("-w");
            argv.add(workdir);
        }
        if (env != null) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                argv.add("-e");
                argv.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        argv.add(containerId);
        argv.add("bash");
        argv.add("-c");
        argv.add(command);
        return runCommand(argv, commandTimeout).thenApply(result -> new ExecResult(
                result.getStdout(),
                result.getStderr(),
                result.getReturncode(),
                result.getReturncode() == -1 && result.getStderr().toLowerCase(Locale.ROOT).contains("timed out")));
    }

    public CompletableFuture<Boolean> copyTo(Path src, String dst) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(false);
        }
        return runCommand(List.of(resolveDockerPath(), "cp", src.toString(), containerId + ":" + dst), 60)
                .thenApply(result -> result.getReturncode() == 0);
    }

    public CompletableFuture<Boolean> copyFrom(String src, Path dst) {
        if (!isRunning()) {
            return CompletableFuture.completedFuture(false);
        }
        return runCommand(List.of(resolveDockerPath(), "cp", containerId + ":" + src, dst.toString()), 60)
                .thenApply(result -> result.getReturncode() == 0);
    }

    protected CompletableFuture<ExecResult> runCommand(List<String> command, int commandTimeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                Process process = builder.start();
                boolean finished = process.waitFor(commandTimeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ExecResult("", "Command timed out after " + commandTimeout + "s", -1, true);
                }
                String stdout = decode(readAllBytes(process.getInputStream()));
                String stderr = decode(readAllBytes(process.getErrorStream()));
                return new ExecResult(stdout, stderr, process.exitValue(), false);
            } catch (Exception exception) {
                return new ExecResult("", exception.getMessage() == null ? exception.toString() : exception.getMessage(), -1, false);
            }
        });
    }

    protected String resolveDockerPath() {
        return findOnPath("docker")
                .orElseThrow(() -> new RuntimeException("docker executable not found in PATH"));
    }

    protected CompletableFuture<Void> delay(long millis) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
        });
    }

    void setContainerIdForTesting(String containerId) {
        this.containerId = containerId;
    }

    private static byte[] readAllBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static String decode(byte[] bytes) {
        List<Charset> encodings = new ArrayList<>();
        encodings.add(StandardCharsets.UTF_8);
        if (isWindows()) {
            encodings.add(Charset.forName("GBK"));
            encodings.add(Charset.forName("windows-936"));
        }
        encodings.add(StandardCharsets.ISO_8859_1);
        for (Charset encoding : encodings) {
            try {
                return new String(bytes, encoding);
            } catch (Exception ignored) {
                // Try the next candidate encoding.
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Optional<String> findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            path = System.getenv("Path");
        }
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String separator = Pattern.quote(System.getProperty("path.separator"));
        for (String entry : path.split(separator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry).resolve(executable);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toString());
            }
            Path exeCandidate = Path.of(entry).resolve(executable + ".exe");
            if (Files.isRegularFile(exeCandidate)) {
                return Optional.of(exeCandidate.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String shortenContainerId(String currentContainerId) {
        if (currentContainerId == null) {
            return "";
        }
        return currentContainerId.length() >= 12 ? currentContainerId.substring(0, 12) : currentContainerId;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return error;
    }
}
