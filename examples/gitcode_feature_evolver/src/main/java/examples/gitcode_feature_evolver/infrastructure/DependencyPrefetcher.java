/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;
import examples.gitcode_issue_evolver.infrastructure.ProcessEnvironmentPolicy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Populates an isolated per-Job Maven cache in a credential-free networked container.
 *
 * @since 0.1.12
 */
public final class DependencyPrefetcher {
    private static final String SUREFIRE_PLUGIN = "maven-surefire-plugin";
    private static final Pattern JOB_ID = Pattern.compile("[0-9a-fA-F-]{16,64}");
    private static final int MAX_OUTPUT = 16_000;
    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofHours(1).toMillis();
    private final FeatureEvolvingConfig config;
    private final ProcessExecutor executor;
    private final AtomicLong lastCleanupAt = new AtomicLong();

    /** Create the trusted prefetch boundary. */
    public DependencyPrefetcher(FeatureEvolvingConfig config) {
        this(config, DependencyPrefetcher::runProcess);
    }

    DependencyPrefetcher(FeatureEvolvingConfig config, ProcessExecutor executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /** @return isolated Maven cache for the supplied Job */
    public Path cacheFor(FeatureJob job) {
        String id = Objects.requireNonNull(job, "job must not be null").identity().id();
        if (!JOB_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Feature Job ID is unsafe for dependency cache use");
        }
        Path root = config.dependencyPrefetchCacheRoot().toAbsolutePath().normalize();
        Path cache = root.resolve(id).resolve("m2").normalize();
        if (!cache.startsWith(root)) {
            throw new IllegalArgumentException("Feature dependency cache escaped its root");
        }
        return cache;
    }

    /** Mark a terminal Job cache for delayed retention cleanup. */
    public void markTerminal(FeatureJob job) {
        Path cache = cacheFor(job);
        if (!Files.isDirectory(cache)) {
            return;
        }
        Path marker = cache.getParent().resolve(".terminal");
        try {
            Files.createDirectories(marker.getParent());
            if (!Files.exists(marker)) {
                Files.createFile(marker);
                Files.setLastModifiedTime(marker, FileTime.fromMillis(System.currentTimeMillis()));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to mark terminal dependency cache", ex);
        }
    }

    /** Delete only terminal Job caches whose configured retention has elapsed. */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        long previous = lastCleanupAt.get();
        if (previous > 0L && now - previous < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanupAt.compareAndSet(previous, now)) {
            return;
        }
        Path root = config.dependencyPrefetchCacheRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            return;
        }
        long cutoff = now
                - Duration.ofHours(config.dependencyPrefetchRetentionHours()).toMillis();
        try (var directories = Files.list(root)) {
            directories.filter(Files::isDirectory).filter(path -> JOB_ID.matcher(
                    path.getFileName().toString()).matches()).forEach(path -> {
                        Path marker = path.resolve(".terminal");
                        try {
                            if (Files.isRegularFile(marker) && !Files.isSymbolicLink(marker)
                                    && Files.getLastModifiedTime(marker).toMillis() <= cutoff) {
                                deleteTree(path, root);
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(
                                    "Unable to inspect terminal dependency cache", ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to clean terminal dependency caches", ex);
        }
    }

    /** Populate a feature-source dependency cache without running tests. */
    public Result prefetchFeature(FeatureJob job, Path worktree, List<String> dirtyPaths) {
        Result preparation = prepare(job, dirtyPaths);
        if (!preparation.passed()) {
            return preparation;
        }
        Path cache = cacheFor(job);
        List<String> command = baseCommand(worktree, cache);
        command.addAll(List.of("--workdir=/workspace", config.containerImage(), "mvn", "-B",
                "-ntp", "-Dmaven.repo.local=/m2", "-DskipTests", "dependency:go-offline"));
        return classify(executor.execute(command, config.dataDir(), timeout()));
    }

    /** Populate dependencies for merged source plus the separate system-test repository. */
    public Result prefetchSystemTest(FeatureJob job, Path sourceWorktree,
                                     Path testWorktree, List<String> dirtyPaths) {
        Result preparation = prepare(job, dirtyPaths);
        if (!preparation.passed()) {
            return preparation;
        }
        String sourceVersion;
        String surefireVersion;
        try {
            sourceVersion = MavenProjectVersionResolver.resolve(sourceWorktree);
            MavenProjectVersionResolver.ensureTargetMountpoint(sourceWorktree);
            surefireVersion = MavenPluginVersionResolver.resolve(testWorktree, SUREFIRE_PLUGIN);
        } catch (MavenProjectVersionResolver.ProjectVersionException
                 | MavenPluginVersionResolver.PluginVersionException ex) {
            return new Result(Status.BUILD_CONTRACT_INVALID, ex.getMessage());
        }
        Path cache = cacheFor(job);
        List<String> command = systemTestCommand(
                sourceWorktree, testWorktree, cache, sourceVersion, surefireVersion);
        return classify(executor.execute(command, config.dataDir(), timeout()));
    }

    private Result prepare(FeatureJob job, List<String> dirtyPaths) {
        if (!config.dependencyPrefetchEnabled()) {
            return new Result(Status.DEPENDENCY_UNAVAILABLE,
                    "Automatic dependency prefetch is disabled");
        }
        if (buildContractChanged(dirtyPaths)) {
            return new Result(Status.POLICY_VIOLATION,
                    "pom.xml or .mvn changed from the trusted target baseline");
        }
        Path cache = cacheFor(job);
        try {
            Files.createDirectories(cache);
            if (isEmpty(cache)) {
                copySharedCache(config.containerMavenCache(), cache);
            }
            return new Result(Status.PASSED, "Isolated Maven cache prepared");
        } catch (IOException ex) {
            return new Result(Status.TRANSIENT, "Unable to prepare isolated Maven cache");
        }
    }

    private List<String> baseCommand(Path worktree, Path cache) {
        FeatureEvolvingConfig.ContainerLimits limits = config.containerLimits();
        List<String> command = new ArrayList<>(List.of(config.containerRuntime(), "run", "--rm",
                "--pull=never", "--network=slirp4netns", "--http-proxy=false",
                "--read-only=true", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--pids-limit=" + limits.pidsLimit(),
                "--memory=" + limits.memoryMb() + "m", "--cpus=" + limits.cpus(),
                "--userns=keep-id:uid=" + config.containerUser().replace(':', ',')
                        .replace(",", ",gid="),
                "--user=" + config.containerUser(),
                "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=256m",
                "--tmpfs=/native-tmp:rw,exec,nosuid,nodev,size=64m",
                "--env=HOME=/tmp", "--env=MAVEN_CONFIG=/tmp/.m2",
                "--env=JAVA_TOOL_OPTIONS=-Duser.home=/tmp -Djansi.tmpdir=/native-tmp "
                        + "-Dorg.sqlite.tmpdir=/native-tmp",
                "--mount=type=bind,src=/dev/null,dst=/workspace/.git,ro=true",
                "--volume=" + normalized(worktree) + ":/workspace:rw,Z",
                "--volume=" + cache + ":/m2:rw,Z"));
        return command;
    }

    private List<String> systemTestCommand(Path source, Path tests, Path cache,
                                           String sourceVersion, String surefireVersion) {
        List<String> command = baseCommand(source, cache);
        int mount = command.indexOf("--mount=type=bind,src=/dev/null,dst=/workspace/.git,ro=true");
        command.set(mount, "--mount=type=bind,src=/dev/null,dst=/source/.git,ro=true");
        int volume = command.indexOf("--volume=" + normalized(source) + ":/workspace:rw,Z");
        command.set(volume, "--volume=" + normalized(source) + ":/source:ro,Z");
        command.add("--tmpfs=/source/target:rw,noexec,nosuid,nodev,size=2048m");
        command.add("--env=FEATURE_SOURCE_VERSION=" + sourceVersion);
        command.add("--env=SUREFIRE_VERSION=" + surefireVersion);
        command.add("--mount=type=bind,src=/dev/null,dst=/tests/.git,ro=true");
        command.add("--volume=" + normalized(tests) + ":/tests:rw,Z");
        String script = "set -eu; mvn -B -ntp -Dmaven.repo.local=/m2 -Dmaven.test.skip=true "
                + "-f /source/pom.xml install; mvn -B -ntp -Dmaven.repo.local=/m2 -DskipTests "
                + "-Dagent-core-java.version=\"$FEATURE_SOURCE_VERSION\" -f /tests/pom.xml "
                + "dependency:go-offline; mvn -B -ntp -Dmaven.repo.local=/m2 "
                + "-f /tests/pom.xml dependency:get "
                + "-Dartifact=org.apache.maven.surefire:surefire-junit-platform:"
                + "\"$SUREFIRE_VERSION\"";
        command.addAll(List.of("--workdir=/tests", config.containerImage(),
                "sh", "-eu", "-c", script));
        return command;
    }

    private static void copySharedCache(Path source, Path target) throws IOException {
        Path normalizedSource = normalized(source);
        validateSharedCache(normalizedSource);
        if (reflinkCopy(normalizedSource, target)) {
            return;
        }
        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = normalizedSource.relativize(directory);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, target.resolve(normalizedSource.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void validateSharedCache(Path source) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                    throws IOException {
                if (attrs.isSymbolicLink()) {
                    throw new IOException("Shared Maven cache contains a symbolic link");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                    throw new IOException("Shared Maven cache contains a non-regular file");
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean reflinkCopy(Path source, Path target) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("cp", "--archive", "--reflink=auto",
                    source + "/.", target.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            ProcessEnvironmentPolicy.sanitize(builder);
            process = builder.start();
            return process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void deleteTree(Path target, Path requiredRoot) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(requiredRoot) || normalized.equals(requiredRoot)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("Refused unsafe dependency-cache cleanup target");
        }
        Files.walkFileTree(normalized, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (attrs.isSymbolicLink()) {
                    throw new IOException("Dependency cache contains a symbolic link");
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Execution runProcess(List<String> command, Path directory, Duration timeout) {
        Process process = null;
        ExecutorService reader = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true);
            ProcessEnvironmentPolicy.sanitize(builder);
            process = builder.start();
            Process running = process;
            reader = singleTaskExecutor("feature-prefetch-output");
            Future<String> output = reader.submit(() -> read(running));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Execution(124, "Dependency prefetch timed out");
            }
            return new Execution(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Execution(130, "Dependency prefetch was interrupted");
        } catch (IOException | ExecutionException | TimeoutException ex) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Execution(125, "Unable to execute dependency prefetch container");
        } finally {
            if (reader != null) {
                reader.shutdownNow();
            }
        }
    }

    private static ExecutorService singleTaskExecutor(String name) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new AutoEvolvingThreadFactory(name),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static String read(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                output.append(buffer, 0, count);
                if (output.length() > MAX_OUTPUT) {
                    output.delete(0, output.length() - MAX_OUTPUT);
                }
            }
        }
        return output.toString();
    }

    private static Result classify(Execution execution) {
        if (execution.exitCode() == 0) {
            return new Result(Status.PASSED, "Dependency prefetch completed");
        }
        if (execution.exitCode() == 124 || execution.exitCode() == 125
                || execution.exitCode() == 130) {
            return new Result(Status.TRANSIENT, "Dependency prefetch infrastructure failed");
        }
        String lower = execution.output().toLowerCase(Locale.ROOT);
        if (transientNetworkFailure(lower)) {
            return new Result(Status.TRANSIENT, "Dependency repository access failed transiently");
        }
        if (lower.contains("could not resolve") || lower.contains("failure to find")
                || lower.contains("could not find artifact")) {
            return new Result(Status.DEPENDENCY_UNAVAILABLE,
                    "A declared dependency is unavailable from trusted POM repositories");
        }
        return new Result(Status.BUILD_CONTRACT_INVALID,
                "Trusted source or system-test build contract failed during dependency prefetch");
    }

    private static boolean transientNetworkFailure(String output) {
        return output.contains("connection timed out") || output.contains("connect timed out")
                || output.contains("read timed out") || output.contains("connection reset")
                || output.contains("unknown host")
                || output.contains("temporary failure in name resolution")
                || output.contains("status code: 429")
                || output.matches("(?s).*status code: 5[0-9][0-9].*");
    }

    private static boolean buildContractChanged(List<String> paths) {
        return paths != null && paths.stream().anyMatch(path -> "pom.xml".equals(path)
                || path.endsWith("/pom.xml") || ".mvn".equals(path)
                || path.startsWith(".mvn/") || path.contains("/.mvn/"));
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static Path normalized(Path path) {
        Path normalized = Objects.requireNonNull(path, "path must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Required dependency-prefetch directory is unavailable");
        }
        return normalized;
    }

    private Duration timeout() {
        return Duration.ofMinutes(config.containerTimeoutMinutes());
    }

    /** Classified prefetch result. */
    public record Result(Status status, String summary) {
        /** Normalize the bounded summary. */
        public Result {
            status = Objects.requireNonNull(status, "status must not be null");
            summary = summary == null ? "" : summary;
        }

        /** @return whether preparation or prefetch passed */
        public boolean passed() {
            return status == Status.PASSED;
        }
    }

    /** Stable prefetch statuses. */
    public enum Status {
        PASSED,
        DEPENDENCY_UNAVAILABLE,
        BUILD_CONTRACT_INVALID,
        TRANSIENT,
        POLICY_VIOLATION
    }

    record Execution(int exitCode, String output) {
    }

    @FunctionalInterface
    interface ProcessExecutor {
        Execution execute(List<String> command, Path directory, Duration timeout);
    }
}
