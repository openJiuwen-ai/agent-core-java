/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_evolver_common.infrastructure.EvolverMavenProjectVersion;
import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.infrastructure.CIGateResult;
import examples.gitcode_issue_evolver.infrastructure.CIGateRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Runs the Controller-owned JiuwenTestJava smoke selection against the current Issue Worktree. */
final class IssueSmokeTestRunner {
    private static final int MAX_OUTPUT_TEXT = 6_000;
    private static final long MAX_FINGERPRINT_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_FINGERPRINT_FILES = 20_000;
    private static final List<String> DEPENDENCY_MARKERS = List.of(
            "could not resolve dependencies", "could not transfer artifact",
            "pluginresolutionexception", "unknown host", "connection timed out",
            "read timed out", "connection reset");
    private final boolean isEnabled;
    private final Path repository;
    private final List<String> selectors;
    private final Duration timeout;
    private final PlanExecutor executor;
    private final String repositoryFingerprint;

    IssueSmokeTestRunner(AutoEvolvingConfig config) {
        this(config, (workspace, commands, timeout) ->
                new CIGateRunner(workspace.toString(), commands, timeout).run());
    }

    IssueSmokeTestRunner(AutoEvolvingConfig config, PlanExecutor executor) {
        AutoEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        this.isEnabled = required.isSmokeTestEnabled();
        this.repository = normalized(required.getSmokeTestRepository());
        this.selectors = List.copyOf(required.getSmokeTestSelectors());
        this.timeout = Duration.ofMinutes(required.getSmokeTestTimeoutMinutes());
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.repositoryFingerprint = isEnabled ? fingerprintRepository(repository, selectors) : "disabled";
    }

    boolean isEnabled() {
        return isEnabled;
    }

    String fingerprint() {
        return repositoryFingerprint;
    }

    List<String> selectors() {
        return selectors;
    }

    Result run(Path sourceWorktree) {
        if (!isEnabled) {
            return Result.passed("Smoke test is disabled");
        }
        String sourceVersion;
        try {
            sourceVersion = EvolverMavenProjectVersion.resolve(sourceWorktree);
        } catch (EvolverMavenProjectVersion.ProjectVersionException ex) {
            return Result.failed("SMOKE_BUILD_CONTRACT_FAILED", "CONFIGURATION", ex.getMessage());
        }
        CIGateResult gate = executor.run(sourceWorktree, commands(sourceVersion), timeout);
        String output = boundedOutput(gate);
        if (gate.isPassed()) {
            return Result.passed("JiuwenTestJava smoke selection passed");
        }
        if (gate.resolvedFailureType().isInfrastructureFailure()
                || hasDependencyFailure(output)) {
            return Result.transientFailure(output);
        }
        return Result.failed("SMOKE_TEST_FAILED", "AGENT_CORRECTABLE", output);
    }

    private List<List<String>> commands(String sourceVersion) {
        List<String> install = List.of("mvn", "-B", "-ntp", "-Dmaven.test.skip=true", "install");
        List<String> smoke = List.of(
                "mvn", "-B", "-ntp", "-f", repository.resolve("pom.xml").toString(),
                "-Dagent-core-java.version=" + sourceVersion,
                "-Dtest=" + String.join(",", selectors), "clean", "test");
        return List.of(install, smoke);
    }

    private static boolean hasDependencyFailure(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        return DEPENDENCY_MARKERS.stream().anyMatch(lower::contains);
    }

    private static String boundedOutput(CIGateResult gate) {
        String output = gate.getErrors();
        if (output == null || output.isBlank()) {
            output = String.join(System.lineSeparator(),
                    gate.getGateOutputs() == null ? List.of() : gate.getGateOutputs());
        }
        String normalized = output == null ? "" : output.strip();
        return normalized.substring(Math.max(0, normalized.length() - MAX_OUTPUT_TEXT));
    }

    private static String fingerprintRepository(Path repository, List<String> selectors) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String selector : selectors) {
                update(digest, "selector=" + selector + System.lineSeparator());
            }
            List<Path> files = fingerprintFiles(repository);
            if (files.size() > MAX_FINGERPRINT_FILES) {
                throw new IllegalStateException("Smoke test repository exceeds the file-count limit");
            }
            long totalBytes = 0L;
            for (Path file : files) {
                long size = Files.size(file);
                totalBytes = Math.addExact(totalBytes, size);
                if (totalBytes > MAX_FINGERPRINT_BYTES) {
                    throw new IllegalStateException("Smoke test repository exceeds the fingerprint size limit");
                }
                update(digest, repository.relativize(file).toString().replace('\\', '/'));
                updateFile(digest, file);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to fingerprint smoke test repository", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Smoke test repository fingerprint is too large", ex);
        }
    }

    private static List<Path> fingerprintFiles(Path repository) throws IOException {
        List<Path> files = new ArrayList<>();
        Path pom = repository.resolve("pom.xml");
        if (Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)) {
            files.add(pom);
        }
        addTree(files, repository.resolve("src/test/java"));
        addTree(files, repository.resolve("src/test/resources"));
        files.sort(Path::compareTo);
        return List.copyOf(files);
    }

    private static void addTree(List<Path> files, Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(path))
                    .forEach(files::add);
        }
    }

    private static void updateFile(MessageDigest digest, Path file) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Path normalized(Path path) {
        if (path == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    enum Status {
        PASSED,
        FAILED,
        TRANSIENT
    }

    record Result(Status status, String code, String category, String output) {
        private static Result passed(String summary) {
            return new Result(Status.PASSED, "", "", summary);
        }

        private static Result failed(String code, String category, String output) {
            return new Result(Status.FAILED, code, category, output == null ? "" : output);
        }

        private static Result transientFailure(String output) {
            return new Result(Status.TRANSIENT, "SMOKE_INFRASTRUCTURE_FAILED",
                    "TRANSIENT_INFRASTRUCTURE", output == null ? "" : output);
        }
    }

    @FunctionalInterface
    interface PlanExecutor {
        CIGateResult run(Path workspace, List<List<String>> commands, Duration timeout);
    }
}
