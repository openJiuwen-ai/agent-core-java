/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;
import examples.gitcode_issue_evolver.infrastructure.ProcessEnvironmentPolicy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs fixed Maven profiles in a rootless, networkless, credential-free Podman container.
 *
 * @since 0.1.12
 */
public final class RootlessContainerGateRunner {
    private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration OUTPUT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_OUTPUT_CHARS = 16_000;
    private final FeatureEvolvingConfig config;
    private final ProcessExecutor executor;

    /**
     * Create a production rootless container runner.
     *
     * @param config validated feature configuration
     */
    public RootlessContainerGateRunner(FeatureEvolvingConfig config) {
        this(config, RootlessContainerGateRunner::executeProcess);
    }

    RootlessContainerGateRunner(FeatureEvolvingConfig config, ProcessExecutor executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Probe the mandatory rootless runtime and pinned local image.
     *
     * @return non-sensitive readiness failures
     */
    public List<String> readinessErrors() {
        List<String> errors = new ArrayList<>();
        Execution rootless = executor.execute(List.of(config.containerRuntime(), "info",
                "--format={{.Host.Security.Rootless}}"), config.dataDir(), READINESS_TIMEOUT);
        if (rootless.exitCode() != 0 || !rootless.output().strip().equalsIgnoreCase("true")) {
            errors.add("rootless Podman is unavailable for the service account");
            return List.copyOf(errors);
        }
        Execution image = executor.execute(List.of(config.containerRuntime(), "image", "exists",
                config.containerImage()), config.dataDir(), READINESS_TIMEOUT);
        if (image.exitCode() != 0) {
            errors.add("the digest-pinned feature test image is not present in rootless Podman storage");
        }
        return List.copyOf(errors);
    }

    /**
     * Run one fixed verification profile.
     *
     * @param profile RED or final full-suite profile
     * @param worktree persistent feature Worktree
     * @return classified container result
     */
    public ContainerGateResult run(Profile profile, Path worktree) {
        Profile required = Objects.requireNonNull(profile, "profile must not be null");
        Path root = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new ContainerGateResult(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                    1, "Feature Worktree is unavailable", List.of());
        }
        List<String> command = containerCommand(required, root);
        Duration timeout = Duration.ofMinutes(config.containerTimeoutMinutes());
        Execution execution = executor.execute(command, config.dataDir(), timeout);
        return classify(required, command, execution);
    }

    private List<String> containerCommand(Profile profile, Path worktree) {
        FeatureEvolvingConfig.ContainerLimits limits = config.containerLimits();
        UserIdentity identity = UserIdentity.parse(config.containerUser());
        List<String> command = new ArrayList<>(List.of(
                config.containerRuntime(), "run", "--rm", "--pull=never", "--network=none",
                "--http-proxy=false",
                "--read-only=true", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--pids-limit=" + limits.pidsLimit(), "--memory=" + limits.memoryMb() + "m",
                "--cpus=" + limits.cpus(), "--userns=keep-id:uid=" + identity.uid()
                        + ",gid=" + identity.gid(), "--user=" + config.containerUser(),
                "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=256m", "--env=HOME=/tmp",
                "--mount=type=bind,src=/dev/null,dst=/workspace/.git,ro=true",
                "--volume=" + worktree + ":/workspace:rw,Z",
                "--volume=" + config.containerMavenCache() + ":/m2:ro,Z",
                "--workdir=/workspace", config.containerImage(),
                "mvn", "-B", "-ntp", "-o", "-Dmaven.repo.local=/m2"));
        command.addAll(profile.mavenArguments());
        return List.copyOf(command);
    }

    private static ContainerGateResult classify(Profile profile, List<String> command,
                                                 Execution execution) {
        String output = sanitize(execution.output());
        if (execution.timedOut()) {
            return result(ContainerGateResult.Outcome.TIMED_OUT, execution, output, command);
        }
        if (execution.exitCode() == 0) {
            ContainerGateResult.Outcome outcome = profile == Profile.RED
                    ? ContainerGateResult.Outcome.TEST_FAILED : ContainerGateResult.Outcome.PASSED;
            return result(outcome, execution, output, command);
        }
        String lower = output.toLowerCase(Locale.ROOT);
        if (dependencyMissing(lower)) {
            return result(ContainerGateResult.Outcome.DEPENDENCY_MISSING, execution, output, command);
        }
        if (execution.exitCode() == 125 || lower.contains("error: crun")
                || lower.contains("cannot connect to podman") || lower.contains("permission denied")) {
            return result(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED, execution, output, command);
        }
        if (profile == Profile.RED && isTrustworthyRed(lower)) {
            return result(ContainerGateResult.Outcome.EXPECTED_RED, execution, output, command);
        }
        return result(ContainerGateResult.Outcome.TEST_FAILED, execution, output, command);
    }

    private static boolean dependencyMissing(String output) {
        return output.contains("could not resolve dependencies")
                || output.contains("cannot access central in offline mode")
                || output.contains("has not been downloaded from it before")
                || output.contains("failure to find") && output.contains("offline");
    }

    private static boolean isTrustworthyRed(String output) {
        boolean testReport = output.contains("tests run:")
                && (output.matches("(?s).*failures: [1-9][0-9]*.*")
                || output.matches("(?s).*errors: [1-9][0-9]*.*"));
        return testReport && !output.contains("compilation error")
                && !output.contains("no tests were executed");
    }

    private static ContainerGateResult result(ContainerGateResult.Outcome outcome,
                                              Execution execution, String output,
                                              List<String> command) {
        return new ContainerGateResult(outcome, execution.exitCode(), output, redactCommand(command));
    }

    private static List<String> redactCommand(List<String> command) {
        return command.stream().map(argument -> argument.startsWith("--volume=")
                ? "--volume=[REDACTED_PATH]" : argument).toList();
    }

    private static String sanitize(String text) {
        String value = text == null ? "" : text.replace('\u0000', ' ');
        if (value.length() > MAX_OUTPUT_CHARS) {
            value = value.substring(value.length() - MAX_OUTPUT_CHARS);
        }
        return value;
    }

    private static Execution executeProcess(List<String> command, Path directory, Duration timeout) {
        Process process = null;
        ExecutorService outputExecutor = null;
        Future<String> output = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            ProcessEnvironmentPolicy.sanitize(builder);
            process = builder.start();
            Process started = process;
            outputExecutor = Executors.newSingleThreadExecutor(
                    new AutoEvolvingThreadFactory("feature-container-output"));
            output = outputExecutor.submit(() -> readBounded(started));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                return new Execution(124, awaitOutput(output), true);
            }
            return new Execution(process.exitValue(), awaitOutput(output), false);
        } catch (IOException ex) {
            return new Execution(125, "Unable to start rootless container process", false);
        } catch (InterruptedException ex) {
            terminate(process);
            Thread.currentThread().interrupt();
            return new Execution(130, "Rootless container process interrupted", false);
        } finally {
            if (outputExecutor != null) {
                outputExecutor.shutdownNow();
            }
        }
    }

    private static void terminate(Process process) {
        if (process == null) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static String readBounded(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (Reader reader = new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                output.append(buffer, 0, count);
                int overflow = output.length() - MAX_OUTPUT_CHARS;
                if (overflow > 0) {
                    output.delete(0, overflow);
                }
            }
        }
        return output.toString();
    }

    private static String awaitOutput(Future<String> output) {
        if (output == null) {
            return "";
        }
        try {
            return output.get(OUTPUT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Container output collection was interrupted";
        } catch (ExecutionException ex) {
            return "Container output could not be decoded";
        } catch (TimeoutException ex) {
            output.cancel(true);
            return "Container output collection timed out";
        }
    }

    /** Fixed verification profiles; Agents cannot supply commands. */
    public enum Profile {
        RED(List.of("-DskipITs", "test")),
        FULL(List.of("verify"));

        private final List<String> mavenArguments;

        Profile(List<String> mavenArguments) {
            this.mavenArguments = List.copyOf(mavenArguments);
        }

        private List<String> mavenArguments() {
            return mavenArguments;
        }
    }

    @FunctionalInterface
    interface ProcessExecutor {
        Execution execute(List<String> command, Path directory, Duration timeout);
    }

    record Execution(int exitCode, String output, boolean timedOut) {
        Execution {
            output = output == null ? "" : output;
        }
    }

    private record UserIdentity(String uid, String gid) {
        private static UserIdentity parse(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("containerUser must use UID:GID format");
            }
            return new UserIdentity(parts[0], parts[1]);
        }
    }
}
