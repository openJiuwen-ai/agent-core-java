/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import com.openjiuwen.autoharness.infra.GitAuth;
import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.job.FeatureJob;
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
 * Creates and resumes one full long-lived Git Worktree per feature job.
 *
 * @since 0.1.12
 */
public final class FeatureWorktreeManager {
    private static final Pattern BRANCH_PATTERN = Pattern.compile(
            "feature-evolving/issue-[1-9][0-9]*-[a-z0-9-]+");
    private static final Pattern CREDENTIAL_URL_PATTERN = Pattern.compile(
            "(?i)(https?://)[^\\s/@]+:[^\\s/@]+@");
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureWorktreeManager.class);
    private final Path repository;
    private final Path worktreeRoot;
    private final String baseBranch;
    private final URI targetCloneUri;
    private final String componentRoot;
    private final String gitCodeToken;
    private final String gitCodeUsername;

    /**
     * Create a manager from validated feature configuration.
     *
     * @param config resolved feature configuration
     */
    public FeatureWorktreeManager(FeatureEvolvingConfig config) {
        FeatureEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        this.repository = required.localRepository().toAbsolutePath().normalize();
        this.worktreeRoot = required.worktreeRoot().toAbsolutePath().normalize();
        this.baseBranch = required.coordinates().baseBranch();
        this.targetCloneUri = required.coordinates().targetCloneUri();
        this.componentRoot = required.componentRoot();
        this.gitCodeToken = required.gitCodeToken();
        this.gitCodeUsername = required.gitCodeUsername();
    }

    /**
     * Resume an owned Worktree or create it from the latest remote base branch.
     *
     * @param job leased feature job
     * @return persistent Worktree identity
     */
    public PreparedWorktree prepare(FeatureJob job) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        validateBranch(required.identity().branch());
        Path worktree = worktreePath(required);
        Path marker = markerPath(required);
        try {
            Files.createDirectories(worktreeRoot);
            if (isOwned(marker, required) && isUsable(worktree, required.identity().branch())) {
                validateComponentRoot(worktree);
                return new PreparedWorktree(worktree, required.identity().branch());
            }
            rejectUnownedResources(worktree, marker, required);
            cleanupOwned(required, worktree, marker);
            create(required, worktree, marker);
            validateComponentRoot(worktree);
            return new PreparedWorktree(worktree, required.identity().branch());
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("Unable to prepare persistent feature Worktree", ex);
        }
    }

    /**
     * Freeze and resume one detached source snapshot after the feature PR is merged.
     *
     * @param job post-merge feature job
     * @return immutable target-base source Worktree and exact revision
     */
    public PreparedMergedSource prepareMergedSource(FeatureJob job) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        Path sourceRoot = mergedSourceRoot();
        Path source = mergedSourcePath(required);
        Path marker = mergedSourceMarker(required);
        try {
            Files.createDirectories(sourceRoot);
            MergedSourceMarker owned = readMergedSourceMarker(marker, required);
            if (owned != null && isDetachedAt(source, owned.revision())) {
                validateComponentRoot(source);
                return new PreparedMergedSource(source, owned.revision());
            }
            if ((Files.exists(source) || Files.exists(marker)) && owned == null) {
                throw new IllegalStateException(
                        "Merged source resources are not owned by this job");
            }
            if (owned == null) {
                run(repository, "fetch merged feature base", targetFetchCommand());
                owned = new MergedSourceMarker(required.identity().id(),
                        revision(targetBaseReference()));
                Files.writeString(marker, owned.jobId() + System.lineSeparator()
                                + owned.revision(), StandardCharsets.UTF_8);
            }
            cleanupMergedSource(source);
            ensureRevisionAvailable(owned.revision());
            run(repository, "create merged source Worktree", List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "worktree", "add", "--detach", source.toString(), owned.revision()));
            validateComponentRoot(source);
            return new PreparedMergedSource(source, owned.revision());
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("Unable to prepare frozen merged source Worktree", ex);
        }
    }

    /**
     * Remove only Worktree resources proven to belong to the supplied terminal job.
     *
     * @param job terminal feature job
     */
    public void cleanup(FeatureJob job) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        try {
            cleanupOwned(required, worktreePath(required), markerPath(required));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to clean terminal feature Worktree", ex);
        }
    }

    private void create(FeatureJob job, Path worktree, Path marker) throws IOException {
        if (branchExists(job.identity().branch())) {
            throw new IllegalStateException("Feature branch already exists without an owned Worktree");
        }
        Files.writeString(marker, job.identity().id() + "\n" + job.identity().branch(),
                StandardCharsets.UTF_8);
        run(repository, "fetch feature base", targetFetchCommand());
        run(repository, "prune Git Worktrees", List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "worktree", "prune"));
        run(repository, "create feature Worktree", List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "worktree", "add", "-b", job.identity().branch(), worktree.toString(),
                targetBaseReference()));
    }

    private void ensureRevisionAvailable(String revision) {
        if (commitExists(revision)) {
            return;
        }
        run(repository, "refetch merged feature base", targetFetchCommand());
        if (!commitExists(revision)) {
            throw new IllegalStateException("Frozen merged source revision is unavailable");
        }
    }

    private String revision(String reference) {
        CommandResult result = execute(repository, List.of(
                "git", "-C", repository.toString(), "rev-parse", "--verify",
                reference + "^{commit}"));
        String revision = result.output().strip();
        if (!result.success() || !revision.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalStateException("Unable to resolve the merged source revision");
        }
        return revision;
    }

    private boolean commitExists(String revision) {
        return execute(repository, List.of(
                "git", "-C", repository.toString(), "cat-file", "-e",
                revision + "^{commit}")).success();
    }

    List<String> targetFetchCommand() {
        String sourceReference = "refs/heads/" + baseBranch;
        String refspec = "+" + sourceReference + ":" + targetBaseReference();
        return List.of("git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "fetch", "--prune", targetCloneUri.toString(), refspec);
    }

    String targetBaseReference() {
        return "refs/remotes/feature-target/" + baseBranch;
    }

    private void validateComponentRoot(Path worktree) {
        Path component = ".".equals(componentRoot)
                ? worktree : worktree.resolve(componentRoot).normalize();
        if (!component.startsWith(worktree) || !Files.isDirectory(component)) {
            throw new IllegalStateException("Configured componentRoot is absent from the feature Worktree");
        }
    }

    private void rejectUnownedResources(Path worktree, Path marker, FeatureJob job) throws IOException {
        if ((Files.exists(worktree) || Files.exists(marker)) && !isOwned(marker, job)) {
            throw new IllegalStateException("Feature Worktree resources are not owned by this job");
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

    private boolean isDetachedAt(Path worktree, String revision) {
        if (!Files.isDirectory(worktree)) {
            return false;
        }
        CommandResult result = execute(worktree, List.of(
                "git", "-C", worktree.toString(), "rev-parse", "HEAD"));
        return result.success() && revision.equalsIgnoreCase(result.output().strip());
    }

    private void cleanupOwned(FeatureJob job, Path worktree, Path marker) throws IOException {
        if (!isOwned(marker, job)) {
            return;
        }
        if (Files.exists(worktree)) {
            CommandResult removal = execute(repository, List.of(
                    "git", "-C", repository.toString(), "worktree", "remove", "--force",
                    worktree.toString()));
            if (!removal.success()) {
                LOGGER.warn("Unable to remove owned feature Worktree through Git: {}", safe(removal.output()));
            }
            deleteTree(worktree);
        }
        execute(repository, List.of("git", "-C", repository.toString(), "worktree", "prune"));
        if (branchExists(job.identity().branch())) {
            CommandResult deletion = execute(repository, List.of(
                    "git", "-C", repository.toString(), "branch", "-D", job.identity().branch()));
            if (!deletion.success()) {
                throw new IOException("Unable to delete owned feature branch");
            }
        }
        Files.deleteIfExists(marker);
    }

    private void cleanupMergedSource(Path worktree) throws IOException {
        if (Files.exists(worktree)) {
            CommandResult removal = execute(repository, List.of(
                    "git", "-C", repository.toString(), "worktree", "remove", "--force",
                    worktree.toString()));
            if (!removal.success()) {
                LOGGER.warn("Unable to remove owned merged source Worktree through Git: {}",
                        safe(removal.output()));
            }
            deleteTree(worktree);
        }
        execute(repository, List.of(
                "git", "-C", repository.toString(), "worktree", "prune"));
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
            outputFile = Files.createTempFile(worktreeRoot, ".feature-git-", ".log");
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            ProcessEnvironmentPolicy.sanitize(builder);
            builder.environment().putAll(GitAuth.buildGitAuthEnv(
                    builder.environment(), gitCodeUsername, gitCodeToken));
            process = builder.start();
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
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

    private Path worktreePath(FeatureJob job) {
        String suffix = job.identity().id().replaceAll("[^A-Za-z0-9]", "");
        suffix = suffix.substring(0, Math.min(suffix.length(), 12));
        Path path = worktreeRoot.resolve("issue-" + job.identity().issue().iid() + "-" + suffix).normalize();
        if (!path.getParent().equals(worktreeRoot)) {
            throw new IllegalArgumentException("Invalid feature Worktree path");
        }
        return path;
    }

    private Path markerPath(FeatureJob job) {
        Path worktree = worktreePath(job);
        return worktreeRoot.resolve("." + worktree.getFileName() + ".owner").normalize();
    }

    private Path mergedSourceRoot() {
        Path root = worktreeRoot.resolve("merged-sources").normalize();
        if (!root.getParent().equals(worktreeRoot)) {
            throw new IllegalArgumentException("Invalid merged source root");
        }
        return root;
    }

    private Path mergedSourcePath(FeatureJob job) {
        String suffix = job.identity().id().replaceAll("[^A-Za-z0-9]", "");
        suffix = suffix.substring(0, Math.min(suffix.length(), 12));
        Path root = mergedSourceRoot();
        Path path = root.resolve("issue-" + job.identity().issue().iid()
                + "-" + suffix).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid merged source Worktree path");
        }
        return path;
    }

    private Path mergedSourceMarker(FeatureJob job) {
        Path source = mergedSourcePath(job);
        return source.getParent().resolve("." + source.getFileName() + ".owner").normalize();
    }

    private static MergedSourceMarker readMergedSourceMarker(Path marker, FeatureJob job)
            throws IOException {
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
        if (lines.size() < 2 || !job.identity().id().equals(lines.get(0))
                || !lines.get(1).matches("[0-9a-fA-F]{40}")) {
            return null;
        }
        return new MergedSourceMarker(lines.get(0), lines.get(1));
    }

    private static boolean isOwned(Path marker, FeatureJob job) throws IOException {
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
        return lines.size() >= 2 && job.identity().id().equals(lines.get(0))
                && job.identity().branch().equals(lines.get(1));
    }

    private static void validateBranch(String branch) {
        if (branch == null || !BRANCH_PATTERN.matcher(branch).matches()) {
            throw new IllegalArgumentException("Invalid feature branch name");
        }
    }

    private static String readOutput(Path outputFile) throws IOException {
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
    }

    private static String safe(String output) {
        String value = output == null ? "" : output;
        String redacted = CREDENTIAL_URL_PATTERN.matcher(value).replaceAll("$1[REDACTED]@");
        String singleLine = redacted.replace('\r', ' ').replace('\n', ' ').strip();
        return singleLine.substring(0, Math.min(singleLine.length(), 1000));
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
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

    /** Persistent feature Worktree identity. */
    public record PreparedWorktree(Path path, String branch) {
        /** Normalize the Worktree path. */
        public PreparedWorktree {
            path = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
            branch = Objects.requireNonNull(branch, "branch must not be null");
        }
    }

    /** Frozen post-merge source Worktree identity. */
    public record PreparedMergedSource(Path path, String revision) {
        /** Normalize the source path and revision. */
        public PreparedMergedSource {
            path = Objects.requireNonNull(path, "path must not be null")
                    .toAbsolutePath().normalize();
            revision = Objects.requireNonNull(revision, "revision must not be null");
        }
    }

    private record MergedSourceMarker(String jobId, String revision) {
    }

    private record CommandResult(int code, String output) {
        private boolean success() {
            return code == 0;
        }
    }
}
