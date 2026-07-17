/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
import java.util.regex.Pattern;

/**
 * Creates and removes short sparse Git Worktrees owned by one durable Job.
 *
 * @since 0.1.12
 */
public final class ExampleWorktreeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleWorktreeManager.class);
    private static final Pattern CREDENTIAL_URL_PATTERN = Pattern.compile(
            "(?i)(https?://)[^\\s/@]+:[^\\s/@]+@");
    private static final Pattern SECRET_PARAMETER_PATTERN = Pattern.compile(
            "(?i)(access_token|token|password)=([^&\\s]+)");
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(3);
    private final Path repository;
    private final Path worktreeRoot;
    private final String baseBranch;

    /**
     * Create a Worktree manager from resolved file configuration.
     *
     * @param config resolved demo configuration
     */
    public ExampleWorktreeManager(AutoEvolvingConfig config) {
        AutoEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        this.repository = required.getLocalRepository().toAbsolutePath().normalize();
        this.worktreeRoot = required.getWorktreeRoot().toAbsolutePath().normalize();
        this.baseBranch = required.getBaseBranch();
    }

    /**
     * Create one short sparse Worktree at the latest configured remote base branch.
     *
     * @param job leased Job
     * @return prepared Worktree and branch
     */
    public PreparedWorktree prepare(EvolutionJob job) {
        EvolutionJob requiredJob = Objects.requireNonNull(job, "job must not be null");
        Path worktree = worktreePath(requiredJob.id());
        Path marker = markerPath(requiredJob.id());
        try {
            Files.createDirectories(worktreeRoot);
            cleanupOwned(requiredJob, worktree, marker);
            if (Files.exists(worktree) || Files.exists(marker)) {
                throw new IllegalStateException("Worktree resources are not clean for this Job");
            }
            if (branchExists(requiredJob.branch())) {
                throw new IllegalStateException("Issue branch already exists and is not owned by this Job");
            }
            Files.writeString(marker, requiredJob.id() + "\n" + requiredJob.branch(),
                    StandardCharsets.UTF_8);
            run(repository, "fetch base branch", List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "fetch", "--prune", "origin", baseBranch));
            run(repository, "prune Worktrees", List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "worktree", "prune"));
            run(repository, "create Worktree", List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "worktree", "add", "--no-checkout", "-b", requiredJob.branch(),
                    worktree.toString(), "origin/" + baseBranch));
            run(worktree, "initialize sparse checkout", List.of(
                    "git", "-C", worktree.toString(), "-c", "core.longpaths=true",
                    "sparse-checkout", "init", "--cone"));
            run(worktree, "set sparse checkout", List.of(
                    "git", "-C", worktree.toString(), "-c", "core.longpaths=true",
                    "sparse-checkout", "set", "src/main", "src/test"));
            run(worktree, "checkout sparse Worktree", List.of(
                    "git", "-C", worktree.toString(), "-c", "core.longpaths=true", "checkout"));
            return new PreparedWorktree(worktree, requiredJob.branch());
        } catch (IOException | IllegalStateException ex) {
            cleanupAfterFailure(requiredJob, worktree, marker, ex);
            throw new WorktreePreparationException("prepare sparse Worktree", safe(ex.getMessage()), ex);
        }
    }

    /**
     * Remove only resources whose marker proves ownership by this Job.
     *
     * @param job Job whose execution has stopped
     */
    public void cleanup(EvolutionJob job) {
        EvolutionJob requiredJob = Objects.requireNonNull(job, "job must not be null");
        Path worktree = worktreePath(requiredJob.id());
        Path marker = markerPath(requiredJob.id());
        try {
            cleanupOwned(requiredJob, worktree, marker);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to clean Job Worktree", ex);
        }
    }

    private void cleanupOwned(EvolutionJob job, Path worktree, Path marker) throws IOException {
        if (!isOwned(marker, job)) {
            return;
        }
        if (Files.exists(worktree)) {
            CommandResult removal = execute(repository, List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "worktree", "remove", "--force", worktree.toString()));
            if (!removal.success()) {
                LOGGER.warn("Unable to remove owned Worktree through Git: {}", safe(removal.output()));
            }
            deleteTree(worktree);
        }
        runQuietly(repository, List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true", "worktree", "prune"));
        if (branchExists(job.branch())) {
            CommandResult branchRemoval = execute(repository, List.of(
                    "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                    "branch", "-D", job.branch()));
            if (!branchRemoval.success()) {
                throw new IOException("Unable to delete owned temporary branch");
            }
        }
        Files.deleteIfExists(marker);
    }

    private void cleanupAfterFailure(EvolutionJob job, Path worktree, Path marker, Exception original) {
        try {
            cleanupOwned(job, worktree, marker);
        } catch (IOException | IllegalStateException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
            LOGGER.warn("Unable to clean partially prepared Worktree", cleanupFailure);
        }
    }
    private boolean branchExists(String branch) {
        CommandResult result = execute(repository, List.of(
                "git", "-C", repository.toString(), "-c", "core.longpaths=true",
                "show-ref", "--verify", "--quiet", "refs/heads/" + branch));
        return result.code() == 0;
    }

    private void run(Path directory, String operation, List<String> command) {
        CommandResult result = execute(directory, command);
        if (!result.success()) {
            throw new IllegalStateException(operation + " failed: " + safe(result.output()));
        }
    }

    private void runQuietly(Path directory, List<String> command) {
        CommandResult result = execute(directory, command);
        if (!result.success()) {
            LOGGER.warn("Git cleanup command failed: {}", safe(result.output()));
        }
    }

    private CommandResult execute(Path directory, List<String> command) {
        Path outputFile = null;
        Process process = null;
        try {
            outputFile = Files.createTempFile(worktreeRoot, ".evolver-git-", ".log");
            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            ProcessEnvironmentPolicy.sanitize(builder);
            process = builder.start();
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!completed) {
                ProcessLifecycle.terminateAndWait(process, TERMINATION_TIMEOUT);
                return new CommandResult(124, "Git command timed out");
            }
            return new CommandResult(process.exitValue(), readOutput(outputFile));
        } catch (IOException ex) {
            return new CommandResult(1, "Unable to start Git command");
        } catch (InterruptedException ex) {
            if (process != null) {
                ProcessLifecycle.terminateAndWait(process, TERMINATION_TIMEOUT);
            }
            Thread.currentThread().interrupt();
            return new CommandResult(130, "Git command interrupted");
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ex) {
                    LOGGER.warn("Unable to delete temporary Git output", ex);
                }
            }
        }
    }

    private Path worktreePath(String jobId) {
        Path path = worktreeRoot.resolve(shortId(jobId)).normalize();
        if (!path.getParent().equals(worktreeRoot)) {
            throw new IllegalArgumentException("Invalid Job Worktree path");
        }
        return path;
    }

    private Path markerPath(String jobId) {
        Path path = worktreeRoot.resolve("." + shortId(jobId) + ".owner").normalize();
        if (!path.getParent().equals(worktreeRoot)) {
            throw new IllegalArgumentException("Invalid Job marker path");
        }
        return path;
    }

    private static String shortId(String jobId) {
        String normalized = Objects.requireNonNull(jobId, "jobId must not be null")
                .replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("jobId has no safe path characters");
        }
        return normalized.substring(0, Math.min(12, normalized.length()));
    }

    private static boolean isOwned(Path marker, EvolutionJob job) throws IOException {
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
        return lines.size() >= 2 && job.id().equals(lines.get(0)) && job.branch().equals(lines.get(1));
    }

    private static String readOutput(Path outputFile) throws IOException {
        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        return output.length() <= 8000 ? output : output.substring(output.length() - 8000);
    }

    private static String safe(String output) {
        if (output == null || output.isBlank()) {
            return "no command output";
        }
        String redacted = CREDENTIAL_URL_PATTERN.matcher(output).replaceAll("$1[REDACTED]@");
        redacted = SECRET_PARAMETER_PATTERN.matcher(redacted).replaceAll("$1=[REDACTED]");
        String singleLine = redacted.replace('\r', ' ').replace('\n', ' ').strip();
        return singleLine.substring(0, Math.min(1000, singleLine.length()));
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
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Prepared Worktree metadata. */
    public record PreparedWorktree(Path path, String branch) {
    }

    private record CommandResult(int code, String output) {
        private boolean success() {
            return code == 0;
        }
    }
}
