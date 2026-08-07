/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import com.openjiuwen.autoharness.infra.GitOperations;
import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_issue_evolver.RepositoryCoordinates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Commits exact controller-approved paths and pushes only the owned feature branch.
 *
 * @since 0.1.12
 */
public final class FeatureGitPublisher {
    private static final Pattern BRANCH_PATTERN = Pattern.compile(
            "feature-evolving/issue-[1-9][0-9]*-[a-z0-9-]+");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
    private final FeatureEvolvingConfig config;
    private final RepositoryCoordinates coordinates;

    /**
     * Create the privileged local-commit and remote-push boundary.
     *
     * @param config validated feature configuration
     */
    public FeatureGitPublisher(FeatureEvolvingConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.coordinates = config.coordinates();
    }

    /**
     * Commit current approved changes and push the exact feature branch.
     *
     * @param job current leased job
     * @param worktree owned persistent Worktree
     * @param allowedScopes controller write scopes for this stage
     * @param commitMessage trusted one-line commit message
     * @return typed publication outcome
     */
    public Result commitAndPush(FeatureJob job, Path worktree, List<String> allowedScopes,
                                String commitMessage) {
        FeatureJob required = Objects.requireNonNull(job, "job must not be null");
        Path root = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || !validBranch(required.identity().branch())) {
            return Result.failure("Invalid owned feature Worktree or branch", false);
        }
        GitOperations git = operations(root);
        if (!required.identity().branch().equals(git.currentBranch())) {
            return Result.failure("Current branch does not match the feature job", false);
        }
        List<String> dirty;
        try {
            dirty = git.listDirtyFiles().stream().map(FeaturePathPolicy::normalize).toList();
        } catch (IllegalArgumentException ex) {
            return Result.failure("Git reported an invalid repository path", false);
        }
        List<String> symbolicLinks = dirty.stream()
                .filter(path -> Files.isSymbolicLink(root.resolve(path).normalize()))
                .toList();
        if (!symbolicLinks.isEmpty()) {
            return Result.failure("Change set contains symbolic links: "
                    + String.join(", ", symbolicLinks), false);
        }
        List<String> violations = FeaturePathPolicy.violations(dirty, allowedScopes);
        if (!violations.isEmpty()) {
            return Result.failure("Change set contains disallowed paths: "
                    + String.join(", ", violations), false);
        }
        CommitOutcome commit = dirty.isEmpty()
                ? existingHead(git, allowedScopes) : commit(git, dirty, commitMessage);
        if (!commit.success()) {
            return Result.failure(commit.error(), commit.retryable());
        }
        if (!SHA_PATTERN.matcher(commit.sha()).matches()) {
            return Result.failure("Feature commit SHA is invalid", false);
        }
        Map<String, Object> pushed = git.push(required.identity().branch());
        if (!Boolean.TRUE.equals(pushed.get("success"))) {
            return Result.failure("Feature branch push failed: " + safe(pushed.get("output")), true);
        }
        if (!commit.sha().equalsIgnoreCase(git.currentHead())) {
            return Result.failure("Feature HEAD changed during publication", false);
        }
        return new Result(true, false, commit.sha(), commit.files(), "");
    }

    /**
     * Read current dirty paths without exposing Git to an Agent.
     *
     * @param worktree owned feature Worktree
     * @return normalized dirty paths
     */
    public List<String> dirtyFiles(Path worktree) {
        Path root = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Feature Worktree is unavailable");
        }
        return List.copyOf(operations(root).listDirtyFiles());
    }

    private CommitOutcome commit(GitOperations git, List<String> dirty, String message) {
        GitOperations.GitCommandResult reset = git.git("reset", "--quiet", "HEAD");
        if (reset.code() != 0) {
            return CommitOutcome.failure("Unable to clear the feature Git index", true);
        }
        GitOperations.GitCommandResult add = git.git(addArguments(dirty));
        if (add.code() != 0) {
            clearIndex(git);
            return CommitOutcome.failure("Unable to stage approved feature files", true);
        }
        List<String> staged = outputPaths(git.git(
                "diff", "--cached", "--name-only", "--diff-filter=ACDMRTUXB", "--").output());
        if (!new LinkedHashSet<>(staged).equals(new LinkedHashSet<>(dirty))) {
            clearIndex(git);
            return CommitOutcome.failure("Staged files differ from the approved feature scope", false);
        }
        String before = git.currentHead();
        GitOperations.GitCommandResult committed = git.git(commitArguments(message));
        if (committed.code() != 0 || before.equals(git.currentHead())) {
            clearIndex(git);
            return CommitOutcome.failure("Unable to create the approved feature commit", true);
        }
        return new CommitOutcome(true, false, git.currentHead(), List.copyOf(staged), "");
    }

    private CommitOutcome existingHead(GitOperations git, List<String> allowedScopes) {
        GitOperations.GitCommandResult result = git.git(
                "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD");
        if (result.code() != 0) {
            return CommitOutcome.failure("Unable to inspect the existing feature commit", true);
        }
        List<String> files = outputPaths(result.output());
        List<String> violations = FeaturePathPolicy.violations(files, allowedScopes);
        if (!violations.isEmpty()) {
            return CommitOutcome.failure("Existing unpushed commit is outside the current stage scope", false);
        }
        return new CommitOutcome(true, false, git.currentHead(), files, "");
    }

    private GitOperations operations(Path worktree) {
        return new GitOperations(worktree.toString(), coordinates.publishCloneUri().toString(),
                coordinates.baseBranch(), coordinates.publishOwner(), coordinates.targetOwner(),
                coordinates.targetName(), coordinates.publishOwner(), config.gitCodeToken(),
                config.gitUserName(), config.gitUserEmail());
    }

    private String[] commitArguments(String commitMessage) {
        String message = commitMessage == null || commitMessage.isBlank()
                ? "feat: advance automated feature workflow"
                : commitMessage.replace('\r', ' ').replace('\n', ' ').strip();
        return new String[]{"-c", "user.name=" + config.gitUserName(),
                "-c", "user.email=" + config.gitUserEmail(),
                "commit", "--no-verify", "-m", message};
    }

    private static String[] addArguments(List<String> files) {
        List<String> arguments = new ArrayList<>(List.of("add", "--"));
        arguments.addAll(files);
        return arguments.toArray(String[]::new);
    }

    private static List<String> outputPaths(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        Set<String> files = new LinkedHashSet<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                files.add(FeaturePathPolicy.normalize(line.strip()));
            }
        }
        return files.stream().sorted().toList();
    }

    private static void clearIndex(GitOperations git) {
        git.git("reset", "--quiet", "HEAD");
    }

    private static boolean validBranch(String branch) {
        return branch != null && BRANCH_PATTERN.matcher(branch).matches();
    }

    private String safe(Object output) {
        String value = output == null ? "" : output.toString();
        if (!config.gitCodeToken().isBlank()) {
            value = value.replace(config.gitCodeToken(), "[REDACTED]");
        }
        value = value.replace('\r', ' ').replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 500));
    }

    /** Git commit and push result. */
    public record Result(boolean success, boolean retryable, String headSha,
                         List<String> committedFiles, String error) {
        /** Normalize and freeze result values. */
        public Result {
            headSha = headSha == null ? "" : headSha;
            committedFiles = committedFiles == null ? List.of() : List.copyOf(committedFiles);
            error = error == null ? "" : error;
        }

        private static Result failure(String error, boolean retryable) {
            return new Result(false, retryable, "", List.of(), error);
        }
    }

    private record CommitOutcome(boolean success, boolean retryable, String sha,
                                 List<String> files, String error) {
        private static CommitOutcome failure(String error, boolean retryable) {
            return new CommitOutcome(false, retryable, "", List.of(), error);
        }
    }
}
