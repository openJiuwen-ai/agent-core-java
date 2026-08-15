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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

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
    private static final int MAX_TEST_SELECTOR_CHARS = 4000;
    private static final String NATIVE_LIBRARY_TMP = "/native-tmp";
    private static final String SOURCE_BUILD_TMP = "/source/target";
    private static final String SYSTEM_TEST_BUILD_TMP = "/tests/target";
    private static final String BUILD_TMPFS_OPTIONS =
            ":rw,noexec,nosuid,nodev,size=2048m,mode=1777";
    private static final String SOURCE_INSTALL_MARKER = "[feature-evolver:step=source-install]";
    private static final String SYSTEM_TEST_MARKER = "[feature-evolver:step=system-test]";
    private static final String BASELINE_TEST_SELECTOR =
            "com.openjiuwen.core.application.schema.ConstrainConfigValidationTest";
    private static final String CONTAINER_JVM_OPTIONS = "-Duser.home=/tmp -Djansi.tmpdir="
            + NATIVE_LIBRARY_TMP + " -Dorg.sqlite.tmpdir=" + NATIVE_LIBRARY_TMP;
    private static final Pattern SINGLE_TEST_SELECTOR = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
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
     * @param profile fixed baseline profile
     * @param worktree persistent feature Worktree
     * @return classified container result
     */
    public ContainerGateResult run(Profile profile, Path worktree) {
        Profile required = Objects.requireNonNull(profile, "profile must not be null");
        if (required != Profile.BASELINE) {
            throw new IllegalArgumentException(
                    "The selected feature profile requires exact test selectors");
        }
        return runProfile(required, worktree, List.of(), config.containerMavenCache());
    }

    /**
     * Run one controller-selected test profile with exact Java test classes.
     *
     * @param profile RED or non-RED targeted profile
     * @param worktree persistent feature Worktree
     * @param testSelectors exact Java test classes from the approved plan
     * @return classified container result
     */
    public ContainerGateResult run(Profile profile, Path worktree, List<String> testSelectors) {
        Profile required = Objects.requireNonNull(profile, "profile must not be null");
        if (!required.requiresTestSelectors()) {
            throw new IllegalArgumentException(
                    "The fixed baseline profile does not accept test selectors");
        }
        return runProfile(required, worktree, exactSelectors(testSelectors),
                config.containerMavenCache());
    }

    /** Run a fixed feature Gate against an isolated per-Job dependency cache. */
    public ContainerGateResult run(Profile profile, Path worktree,
                                   List<String> testSelectors, Path mavenCache) {
        Profile required = Objects.requireNonNull(profile, "profile must not be null");
        List<String> selectors = required.requiresTestSelectors()
                ? exactSelectors(testSelectors) : List.of();
        return runProfile(required, worktree, selectors, normalizedCache(mavenCache));
    }

    private ContainerGateResult runProfile(Profile profile, Path worktree,
                                           List<String> testSelectors, Path mavenCache) {
        Path root = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new ContainerGateResult(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                    1, "Feature Worktree is unavailable", List.of());
        }
        List<String> command = containerCommand(profile, root, testSelectors, mavenCache);
        Duration timeout = Duration.ofMinutes(config.containerTimeoutMinutes());
        Execution execution = executor.execute(command, config.dataDir(), timeout);
        return classify(profile, command, execution);
    }

    /**
     * Build the merged feature artifact and compile or run focused tests in the separate test Worktree.
     *
     * @param profile fixed system-test profile
     * @param sourceWorktree merged feature source
     * @param testWorktree system-test repository Worktree
     * @param testSelectors exact Java test classes for the selected-test profile
     * @return classified credential-free container result
     */
    public ContainerGateResult runSystemTest(SystemTestProfile profile, Path sourceWorktree,
                                             Path testWorktree, List<String> testSelectors) {
        return runSystemTest(profile, sourceWorktree, testWorktree, testSelectors,
                config.containerMavenCache());
    }

    /** Run a system-test Gate against an isolated per-Job dependency cache. */
    public ContainerGateResult runSystemTest(SystemTestProfile profile, Path sourceWorktree,
                                             Path testWorktree, List<String> testSelectors,
                                             Path mavenCache) {
        SystemTestProfile required = Objects.requireNonNull(profile, "profile must not be null");
        Optional<Path> sourceCandidate = normalizedDirectory(sourceWorktree);
        Optional<Path> testCandidate = normalizedDirectory(testWorktree);
        if (sourceCandidate.isEmpty() || testCandidate.isEmpty()) {
            return new ContainerGateResult(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                    1, "Source or system-test Worktree is unavailable", List.of());
        }
        Path source = sourceCandidate.orElseThrow();
        Path tests = testCandidate.orElseThrow();
        String sourceVersion;
        try {
            sourceVersion = MavenProjectVersionResolver.resolve(source);
            MavenProjectVersionResolver.ensureTargetMountpoint(source);
            MavenProjectVersionResolver.ensureTargetMountpoint(tests);
        } catch (MavenProjectVersionResolver.ProjectVersionException ex) {
            return new ContainerGateResult(ContainerGateResult.Outcome.BUILD_CONTRACT_FAILED,
                    1, ex.getMessage(), List.of());
        }
        List<String> exact = exactSelectors(testSelectors);
        String selectors = String.join(",", exact);
        Path selectedPom;
        try {
            selectedPom = SelectedSystemTestPom.create(config.dataDir(), tests, exact);
        } catch (SelectedSystemTestPom.SelectedPomException ex) {
            return new ContainerGateResult(ContainerGateResult.Outcome.BUILD_CONTRACT_FAILED,
                    1, ex.getMessage(), List.of());
        }
        List<String> command = systemTestCommand(required, source, tests,
                new SystemTestInvocation(selectors, sourceVersion, normalizedCache(mavenCache),
                        selectedPom));
        Execution execution = executor.execute(command, config.dataDir(),
                Duration.ofMinutes(config.containerTimeoutMinutes()));
        return classify(Profile.TARGETED, command, execution);
    }

    private List<String> containerCommand(Profile profile, Path worktree,
                                          List<String> testSelectors, Path mavenCache) {
        FeatureEvolvingConfig.ContainerLimits limits = config.containerLimits();
        UserIdentity identity = UserIdentity.parse(config.containerUser());
        List<String> command = new ArrayList<>(List.of(
                config.containerRuntime(), "run", "--rm", "--pull=never", "--network=none",
                "--http-proxy=false",
                "--read-only=true", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--pids-limit=" + limits.pidsLimit(), "--memory=" + limits.memoryMb() + "m",
                "--cpus=" + limits.cpus(), "--userns=keep-id:uid=" + identity.uid()
                        + ",gid=" + identity.gid(), "--user=" + config.containerUser(),
                "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=256m",
                "--tmpfs=" + NATIVE_LIBRARY_TMP + ":rw,exec,nosuid,nodev,size=64m",
                "--env=HOME=/tmp", "--env=MAVEN_CONFIG=/tmp/.m2",
                "--env=JAVA_TOOL_OPTIONS=" + CONTAINER_JVM_OPTIONS,
                "--mount=type=bind,src=/dev/null,dst=/workspace/.git,ro=true",
                "--volume=" + worktree + ":/workspace:rw,Z",
                "--volume=" + mavenCache + ":/m2:ro,Z",
                "--workdir=/workspace", config.containerImage(),
                "mvn", "-B", "-ntp", "-o", "-Dmaven.repo.local=/m2"));
        command.addAll(profile.mavenArguments(testSelectors));
        return List.copyOf(command);
    }

    List<String> systemTestCommand(SystemTestProfile profile, Path sourceWorktree,
                                   Path testWorktree, String selectors) {
        String sourceVersion = MavenProjectVersionResolver.resolve(sourceWorktree);
        List<String> exact = exactSelectors(List.of(selectors.split(",")));
        Path selectedPom = SelectedSystemTestPom.create(config.dataDir(), testWorktree, exact);
        return systemTestCommand(profile, sourceWorktree, testWorktree,
                new SystemTestInvocation(String.join(",", exact), sourceVersion,
                        config.containerMavenCache(), selectedPom));
    }

    private List<String> systemTestCommand(SystemTestProfile profile, Path sourceWorktree,
                                           Path testWorktree, SystemTestInvocation invocation) {
        FeatureEvolvingConfig.ContainerLimits limits = config.containerLimits();
        UserIdentity identity = UserIdentity.parse(config.containerUser());
        String script = "set -eu; printf '%s\\n' '" + SOURCE_INSTALL_MARKER + "'; "
                + "mvn -B -ntp -o -Dmaven.repo.local=/m2 -Dmaven.test.skip=true "
                + "-f /source/pom.xml install; printf '%s\\n' '" + SYSTEM_TEST_MARKER + "'; "
                + "mvn -B -ntp -o -Dmaven.repo.local=/m2 "
                + "-Dagent-core-java.version=\"$FEATURE_SOURCE_VERSION\" "
                + "-f /tests/pom.xml " + profile.mavenGoal(invocation.selectors());
        return List.of(config.containerRuntime(), "run", "--rm", "--pull=never",
                "--network=none", "--http-proxy=false", "--read-only=true", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--pids-limit=" + limits.pidsLimit(),
                "--memory=" + limits.memoryMb() + "m", "--cpus=" + limits.cpus(),
                "--userns=keep-id:uid=" + identity.uid() + ",gid=" + identity.gid(),
                "--user=" + config.containerUser(),
                "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=256m",
                "--tmpfs=" + NATIVE_LIBRARY_TMP + ":rw,exec,nosuid,nodev,size=64m",
                "--tmpfs=" + SOURCE_BUILD_TMP + BUILD_TMPFS_OPTIONS,
                "--tmpfs=" + SYSTEM_TEST_BUILD_TMP + BUILD_TMPFS_OPTIONS,
                "--env=HOME=/tmp", "--env=MAVEN_CONFIG=/tmp/.m2",
                "--env=JAVA_TOOL_OPTIONS=" + CONTAINER_JVM_OPTIONS,
                "--env=FEATURE_SOURCE_VERSION=" + invocation.sourceVersion(),
                "--mount=type=bind,src=/dev/null,dst=/source/.git,ro=true",
                "--mount=type=bind,src=/dev/null,dst=/tests/.git,ro=true",
                "--volume=" + sourceWorktree + ":/source:ro,Z",
                "--volume=" + testWorktree + ":/tests:ro,Z",
                "--volume=" + invocation.selectedPom() + ":/tests/pom.xml:ro,Z",
                "--volume=" + invocation.mavenCache() + ":/m2:O",
                "--workdir=/tests", config.containerImage(), "sh", "-eu", "-c", script);
    }

    private static Optional<Path> normalizedDirectory(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        Path normalized = path.toAbsolutePath().normalize();
        return Files.isDirectory(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private static Path normalizedCache(Path path) {
        return normalizedDirectory(path).orElseThrow(
                () -> new IllegalArgumentException("Maven dependency cache is unavailable"));
    }

    private static List<String> exactSelectors(List<String> supplied) {
        List<String> candidates = supplied == null ? List.of() : supplied;
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        int totalChars = 0;
        for (String candidate : candidates) {
            if (candidate == null || !SINGLE_TEST_SELECTOR.matcher(candidate).matches()) {
                throw new IllegalArgumentException("Test selectors must be exact Java class names");
            }
            totalChars += candidate.length() + 1;
            if (totalChars > MAX_TEST_SELECTOR_CHARS) {
                throw new IllegalArgumentException("Test selectors exceed the fixed size limit");
            }
            selectors.add(candidate);
        }
        if (selectors.isEmpty()) {
            throw new IllegalArgumentException("At least one exact test selector is required");
        }
        return List.copyOf(selectors);
    }

    private static ContainerGateResult classify(Profile profile, List<String> command,
                                                 Execution execution) {
        String output = sanitize(execution.output());
        if (execution.timedOut()) {
            return result(ContainerGateResult.Outcome.TIMED_OUT, execution, output, command);
        }
        if (execution.exitCode() == 130) {
            return result(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                    execution, output, command);
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
        if (execution.exitCode() == 125 || infrastructureFailure(lower)) {
            return result(ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED, execution, output, command);
        }
        if (profile == Profile.RED && isTrustworthyRed(lower)) {
            return result(ContainerGateResult.Outcome.EXPECTED_RED, execution, output, command);
        }
        if (sourceBuildFailure(lower)) {
            return result(ContainerGateResult.Outcome.SOURCE_BUILD_FAILED,
                    execution, output, command);
        }
        if (testCompilationFailure(lower)) {
            return result(ContainerGateResult.Outcome.TEST_COMPILATION_FAILED,
                    execution, output, command);
        }
        if (testDiscoveryFailure(lower)) {
            return result(ContainerGateResult.Outcome.TEST_DISCOVERY_FAILED,
                    execution, output, command);
        }
        if (testFailure(lower)) {
            return result(ContainerGateResult.Outcome.TEST_FAILED, execution, output, command);
        }
        return result(ContainerGateResult.Outcome.UNOBSERVABLE_FAILURE,
                execution, output, command);
    }

    private static boolean dependencyMissing(String output) {
        return output.contains("could not resolve dependencies")
                || output.contains("cannot access central in offline mode")
                || output.contains("has not been downloaded from it before")
                || output.contains("plugin or one of its dependencies could not be resolved")
                || output.contains("could not resolve plugin")
                || (output.contains("no plugin found for prefix")
                && output.contains("offline"))
                || (output.contains("failure to find") && output.contains("offline"));
    }

    private static boolean infrastructureFailure(String output) {
        return output.contains("error: crun") || output.contains("cannot connect to podman")
                || output.contains("oci runtime error")
                || output.contains("runc create failed")
                || output.contains("error mounting \"tmpfs\"")
                || output.contains("permission denied")
                || output.contains("unable to create temporary directory /source/target/")
                || output.contains("unable to create temporary directory /tests/target/")
                || output.contains("unable to create native thread")
                || output.contains("unable to create new native thread")
                || output.contains("pthread_create failed (eagain)")
                || output.contains("possibly out of memory or process/resource limits reached");
    }

    private static boolean testCompilationFailure(String output) {
        return output.contains("compilation error")
                || (output.contains("maven-compiler-plugin")
                && output.contains("compilation failure"));
    }

    private static boolean testDiscoveryFailure(String output) {
        return output.contains("no tests were executed")
                || output.contains("no tests matching pattern");
    }

    private static boolean testFailure(String output) {
        return output.contains("tests run:")
                && (output.matches("(?s).*failures: [1-9][0-9]*.*")
                || output.matches("(?s).*errors: [1-9][0-9]*.*"));
    }

    private static boolean sourceBuildFailure(String output) {
        return output.contains(SOURCE_INSTALL_MARKER) && !output.contains(SYSTEM_TEST_MARKER);
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
            outputExecutor = singleTaskExecutor("feature-container-output");
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

    private static ExecutorService singleTaskExecutor(String name) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new AutoEvolvingThreadFactory(name),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Fixed verification profiles; Agents cannot supply Maven arguments. */
    public enum Profile {
        BASELINE,
        RED,
        TARGETED;

        private boolean requiresTestSelectors() {
            return this != BASELINE;
        }

        private List<String> mavenArguments(List<String> testSelectors) {
            String selectors = this == BASELINE
                    ? BASELINE_TEST_SELECTOR : String.join(",", testSelectors);
            return List.of("-DskipITs", "-Dtest=" + selectors, "-DfailIfNoTests=true",
                    "-Dsurefire.failIfNoSpecifiedTests=true", "test");
        }
    }

    /** Fixed post-merge test-repository profiles. */
    public enum SystemTestProfile {
        SELECTED;

        private String mavenGoal(String selectors) {
            return "-Dtest=\"" + selectors + "\" test";
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

    private record SystemTestInvocation(String selectors, String sourceVersion,
                                        Path mavenCache, Path selectedPom) {
        private SystemTestInvocation {
            selectors = Objects.requireNonNull(selectors, "selectors must not be null");
            sourceVersion = Objects.requireNonNull(
                    sourceVersion, "source version must not be null");
            mavenCache = normalizedCache(mavenCache);
            selectedPom = Objects.requireNonNull(
                    selectedPom, "selected POM must not be null").toAbsolutePath().normalize();
        }
    }
}
