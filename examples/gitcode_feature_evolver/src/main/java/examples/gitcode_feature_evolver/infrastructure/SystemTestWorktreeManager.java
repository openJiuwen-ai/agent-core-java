/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import com.openjiuwen.autoharness.infra.GitAuth;
import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.infrastructure.ProcessEnvironmentPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Creates one service-owned Worktree from the configured system-test repository.
 *
 * @since 0.1.12
 */
public final class SystemTestWorktreeManager {
    private static final Pattern BRANCH_PATTERN = Pattern.compile(
            "feature-evolving/system-test-issue-[1-9][0-9]*-[a-z0-9-]+");
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTestWorktreeManager.class);
    private final Path repository;
    private final Path worktreeRoot;
    private final RepositoryCoordinates coordinates;
    private final URI cloneUri;
    private final String gitCodeToken;
    private final String gitCodeUsername;

    /**
     * Bind system-test Worktrees to the deployment seed repository.
     *
     * @param config validated feature configuration
     */
    public SystemTestWorktreeManager(FeatureEvolvingConfig config) {
        FeatureEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        this.repository = required.localRepository().toAbsolutePath().normalize();
        this.worktreeRoot = required.worktreeRoot().resolve("system-tests")
                .toAbsolutePath().normalize();
        this.coordinates = required.systemTestCoordinates();
        this.cloneUri = coordinates.targetCloneUri();
        this.gitCodeToken = required.systemTestGitCodeToken();
        this.gitCodeUsername = required.systemTestGitCodeUsername();
    }

    /**
     * Resume an owned test Worktree or create it from the latest configured test base.
     *
     * @param job current post-merge feature job
     * @return test Worktree and stable branch
     */
    public PreparedSystemTestWorktree prepare(FeatureJob job) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        String branch = FeatureNaming.systemTestBranch(
                required.identity().issue().iid(), required.identity().issue().title());
        validateBranch(branch);
        Path worktree = worktreePath(required);
        Path marker = markerPath(required);
        try {
            Files.createDirectories(worktreeRoot);
            if (isOwned(marker, required, branch) && isUsable(worktree, branch)) {
                validateLayout(worktree);
                return new PreparedSystemTestWorktree(worktree, branch);
            }
            rejectUnownedResources(worktree, marker, required, branch);
            cleanupOwned(required, branch, worktree, marker);
            create(required, branch, worktree, marker);
            validateLayout(worktree);
            return new PreparedSystemTestWorktree(worktree, branch);
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("Unable to prepare system-test Worktree", ex);
        }
    }

    private void create(FeatureJob job, String branch, Path worktree, Path marker)
            throws IOException {
        if (branchExists(branch)) {
            throw new IllegalStateException(
                    "System-test branch exists without an owned Worktree");
        }
        Files.writeString(marker, job.identity().id() + System.lineSeparator() + branch,
                StandardCharsets.UTF_8);
        run(repository, "fetch system-test base", targetFetchCommand());
        run(repository, "prune Git Worktrees", List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "worktree", "prune"));
        run(repository, "create system-test Worktree", List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "worktree", "add", "-b", branch, worktree.toString(), targetBaseReference()));
    }

    List<String> targetFetchCommand() {
        String source = "refs/heads/" + coordinates.baseBranch();
        String refspec = "+" + source + ":" + targetBaseReference();
        return List.of("git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "fetch", "--prune", cloneUri.toString(), refspec);
    }

    String targetBaseReference() {
        return "refs/remotes/feature-system-test/" + coordinates.baseBranch();
    }

    private void validateLayout(Path worktree) {
        if (!Files.isRegularFile(worktree.resolve("pom.xml"))
                || !Files.isDirectory(worktree.resolve("src/test/java"))) {
            throw new IllegalStateException("System-test repository has no supported Maven test layout");
        }
    }

    private boolean isUsable(Path worktree, String branch) {
        if (!Files.isDirectory(worktree)) {
            return false;
        }
        CommandResult result = execute(worktree, List.of(
                "git", "-C", worktree.toString(), "branch", "--show-current"));
        return result.success() && branch.equals(result.output().strip());
    }

    private static void rejectUnownedResources(Path worktree, Path marker, FeatureJob job,
                                               String branch) throws IOException {
        if ((Files.exists(worktree) || Files.exists(marker))
                && !isOwned(marker, job, branch)) {
            throw new IllegalStateException(
                    "System-test Worktree resources are not owned by this job");
        }
    }

    private void cleanupOwned(FeatureJob job, String branch, Path worktree, Path marker)
            throws IOException {
        if (!isOwned(marker, job, branch)) {
            return;
        }
        if (Files.exists(worktree)) {
            CommandResult removal = execute(repository, List.of(
                    "git", "-C", repository.toString(), "worktree", "remove", "--force",
                    worktree.toString()));
            if (!removal.success()) {
                LOGGER.warn("Unable to remove owned system-test Worktree through Git: {}",
                        safe(removal.output()));
            }
            deleteTree(worktree);
        }
        execute(repository, List.of(
                "git", "-C", repository.toString(), "worktree", "prune"));
        if (branchExists(branch)) {
            CommandResult deletion = execute(repository, List.of(
                    "git", "-C", repository.toString(), "branch", "-D", branch));
            if (!deletion.success()) {
                throw new IOException("Unable to delete owned system-test branch");
            }
        }
        Files.deleteIfExists(marker);
    }

    private boolean branchExists(String branch) {
        return execute(repository, List.of("git", "-C", repository.toString(),
                "show-ref", "--verify", "--quiet", "refs/heads/" + branch)).code() == 0;
    }

    private void run(Path directory, String operation, List<String> command) {
        CommandResult result = execute(directory, command);
        if (!result.success()) {
            throw new IllegalStateException(operation + " failed: " + safe(result.output()));
        }
    }

    private CommandResult execute(Path directory, List<String> command) {
        Path outputFile = null;
        Process process = null;
        try {
            outputFile = Files.createTempFile(worktreeRoot, ".system-test-git-", ".log");
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            ProcessEnvironmentPolicy.sanitize(builder);
            builder.environment().putAll(GitAuth.buildGitAuthEnv(builder.environment(),
                    gitCodeUsername, gitCodeToken));
            process = builder.start();
            if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                return new CommandResult(124, "Git command timed out");
            }
            return new CommandResult(process.exitValue(), readOutput(outputFile));
        } catch (IOException ex) {
            return new CommandResult(1, "Unable to start Git command");
        } catch (InterruptedException ex) {
            terminate(process);
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Git command interrupted");
        } finally {
            deleteOutput(outputFile);
        }
    }

    private Path worktreePath(FeatureJob job) {
        String suffix = job.identity().id().replaceAll("[^A-Za-z0-9]", "");
        suffix = suffix.substring(0, Math.min(suffix.length(), 12));
        Path path = worktreeRoot.resolve("issue-" + job.identity().issue().iid()
                + "-" + suffix).normalize();
        if (!path.getParent().equals(worktreeRoot)) {
            throw new IllegalArgumentException("Invalid system-test Worktree path");
        }
        return path;
    }

    private Path markerPath(FeatureJob job) {
        Path worktree = worktreePath(job);
        return worktreeRoot.resolve("." + worktree.getFileName() + ".owner").normalize();
    }

    private static boolean isOwned(Path marker, FeatureJob job, String branch) throws IOException {
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
        return lines.size() >= 2 && job.identity().id().equals(lines.get(0))
                && branch.equals(lines.get(1));
    }

    private static void validateBranch(String branch) {
        if (!BRANCH_PATTERN.matcher(branch).matches()) {
            throw new IllegalArgumentException("Invalid system-test branch name");
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

    private static String readOutput(Path outputFile) throws IOException {
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
    }

    private static String safe(String output) {
        String value = output == null ? "" : output.replace('\r', ' ').replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private static void deleteOutput(Path outputFile) {
        if (outputFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException ex) {
            outputFile.toFile().deleteOnExit();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
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

    /** Owned post-merge system-test Worktree. */
    public record PreparedSystemTestWorktree(Path path, String branch) {
        /** Normalize the test Worktree path. */
        public PreparedSystemTestWorktree {
            path = Objects.requireNonNull(path, "path must not be null")
                    .toAbsolutePath().normalize();
            branch = Objects.requireNonNull(branch, "branch must not be null");
        }
    }

    private record CommandResult(int code, String output) {
        private boolean success() {
            return code == 0;
        }
    }
}
