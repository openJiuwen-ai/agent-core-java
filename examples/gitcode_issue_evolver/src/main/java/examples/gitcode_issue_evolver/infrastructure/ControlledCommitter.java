/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import com.openjiuwen.autoharness.infra.GitOperations;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Creates one local commit from an exact set of verified repository files.
 *
 * <p>This component exposes no push, pull-request, or merge capability. It clears the index,
 * stages only the supplied paths, verifies the staged set, and then creates one local commit.</p>
 *
 * @since 0.1.12
 */
public final class ControlledCommitter {
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:/.*");
    private final GitOperations git;

    /**
     * Create a local-only committer.
     *
     * @param git Git operations already bound to the isolated Worktree
     */
    public ControlledCommitter(GitOperations git) {
        this.git = Objects.requireNonNull(git, "git must not be null");
    }

    /**
     * Commit exactly the verified dirty paths.
     *
     * @param verifiedFiles repository-relative files admitted by the commit scope
     * @param commitMessage trusted one-line commit message
     * @return typed local commit result
     */
    public CommitResult commit(List<String> verifiedFiles, String commitMessage) {
        PathValidation validation = validatePaths(verifiedFiles);
        if (!validation.valid()) {
            return CommitResult.failure(validation.failureType(), validation.error(), git.statusPorcelain());
        }
        List<String> paths = validation.paths();
        Set<String> dirtyFiles = new LinkedHashSet<>(git.listDirtyFiles());
        if (!dirtyFiles.containsAll(paths)) {
            return CommitResult.failure(CommitFailureType.VALIDATION,
                    "verified commit path is not a current verified change", git.statusPorcelain());
        }
        GitOperations.GitCommandResult reset = git.git("reset", "--quiet", "HEAD");
        if (reset.code() != 0) {
            return CommitResult.failure(CommitFailureType.INFRASTRUCTURE,
                    gitFailure("unable to clear the Git index", reset), git.statusPorcelain());
        }
        GitOperations.GitCommandResult add = git.git(addArguments(paths));
        if (add.code() != 0) {
            clearIndex();
            return CommitResult.failure(CommitFailureType.INFRASTRUCTURE,
                    gitFailure("unable to stage verified files", add), git.statusPorcelain());
        }
        GitOperations.GitCommandResult staged = git.git(
                "diff", "--cached", "--name-only", "--diff-filter=ACDMRTUXB", "--");
        if (staged.code() != 0) {
            clearIndex();
            return CommitResult.failure(CommitFailureType.INFRASTRUCTURE,
                    gitFailure("unable to inspect staged files", staged), git.statusPorcelain());
        }
        List<String> stagedFiles = outputPaths(staged.output());
        if (!new LinkedHashSet<>(stagedFiles).equals(new LinkedHashSet<>(paths))) {
            clearIndex();
            return CommitResult.failure(CommitFailureType.VALIDATION,
                    "staged files differ from the verified commit scope", git.statusPorcelain());
        }
        String beforeHead = git.currentHead();
        GitOperations.GitCommandResult commit = git.git(commitArguments(commitMessage));
        if (commit.code() != 0 || beforeHead.equals(git.currentHead())) {
            clearIndex();
            return CommitResult.failure(CommitFailureType.INFRASTRUCTURE,
                    gitFailure("unable to create the verified local commit", commit), git.statusPorcelain());
        }
        GitOperations.GitCommandResult committed = git.git(
                "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD");
        if (committed.code() != 0) {
            return CommitResult.failure(CommitFailureType.INFRASTRUCTURE,
                    gitFailure("unable to inspect committed files", committed), git.statusPorcelain());
        }
        List<String> committedFiles = outputPaths(committed.output());
        if (!new LinkedHashSet<>(committedFiles).equals(new LinkedHashSet<>(paths))) {
            return CommitResult.failure(CommitFailureType.VALIDATION,
                    "local commit differs from the verified commit scope", git.statusPorcelain());
        }
        return new CommitResult(true, CommitFailureType.NONE, "", git.statusPorcelain(), git.showLastCommitStat(),
                git.currentHead(), committedFiles);
    }

    private PathValidation validatePaths(List<String> verifiedFiles) {
        if (verifiedFiles == null || verifiedFiles.isEmpty()) {
            return PathValidation.failure(CommitFailureType.INFRASTRUCTURE,
                    "verified commit file list must not be empty");
        }
        Set<String> normalizedPaths = new LinkedHashSet<>();
        for (String file : verifiedFiles) {
            String normalized = CommitScope.normalizePath(file);
            try {
                Path path = Path.of(normalized);
                List<String> segments = List.of(normalized.split("/"));
                if (normalized.isBlank() || path.isAbsolute()
                        || WINDOWS_ABSOLUTE_PATH.matcher(normalized).matches()
                        || segments.contains("..") || segments.contains(".")
                        || !CommitScope.isAllowedRepoEditPath(normalized)) {
                    return PathValidation.failure(CommitFailureType.VALIDATION,
                            "unsafe commit path: " + normalized);
                }
            } catch (InvalidPathException ex) {
                return PathValidation.failure(CommitFailureType.VALIDATION, "unsafe commit path");
            }
            normalizedPaths.add(normalized);
        }
        return new PathValidation(true, normalizedPaths.stream().sorted().toList(), "", CommitFailureType.NONE);
    }

    private String[] addArguments(List<String> paths) {
        List<String> arguments = new ArrayList<>(List.of("add", "--"));
        arguments.addAll(paths);
        return arguments.toArray(String[]::new);
    }

    private String[] commitArguments(String commitMessage) {
        String message = commitMessage == null || commitMessage.isBlank()
                ? "chore(auto-harness): commit verified changes"
                : commitMessage.replace('\r', ' ').replace('\n', ' ').strip();
        List<String> arguments = new ArrayList<>();
        if (!git.getUserName().isBlank()) {
            arguments.addAll(List.of("-c", "user.name=" + git.getUserName()));
        }
        if (!git.getUserEmail().isBlank()) {
            arguments.addAll(List.of("-c", "user.email=" + git.getUserEmail()));
        }
        // The verified index is the commit boundary. A pathspec would make Git read those
        // paths from the worktree again and could bypass the staged-content verification.
        arguments.addAll(List.of("commit", "--no-verify", "-m", message));
        return arguments.toArray(String[]::new);
    }

    private static List<String> outputPaths(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String line : output.split("\\R")) {
            String path = CommitScope.normalizePath(line);
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths.stream().sorted().toList();
    }

    private void clearIndex() {
        git.git("reset", "--quiet", "HEAD");
    }

    private static String gitFailure(String summary, GitOperations.GitCommandResult result) {
        String output = result.output() == null ? "" : result.output()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        if (output.isBlank()) {
            return summary;
        }
        return summary + ": " + output.substring(0, Math.min(output.length(), 500));
    }

    private record PathValidation(boolean valid, List<String> paths, String error,
                                  CommitFailureType failureType) {
        private PathValidation {
            paths = List.copyOf(paths);
            failureType = Objects.requireNonNull(failureType, "failureType must not be null");
        }

        private static PathValidation failure(CommitFailureType failureType, String error) {
            return new PathValidation(false, List.of(), error, failureType);
        }
    }

    /**
     * Result of a controlled local commit.
     *
     * @param success whether the exact verified file set was committed
     * @param failureType stable failure classification
     * @param error safe failure description
     * @param statusText Git porcelain status after the attempt
     * @param lastCommitStat latest commit summary after success
     * @param commitSha created local commit SHA
     * @param committedFiles exact paths recorded by the commit
     */
    public record CommitResult(boolean success, CommitFailureType failureType, String error, String statusText,
                               String lastCommitStat, String commitSha, List<String> committedFiles) {
        /**
         * Create an immutable result.
         */
        public CommitResult {
            failureType = Objects.requireNonNull(failureType, "failureType must not be null");
            error = error == null ? "" : error;
            statusText = statusText == null ? "" : statusText;
            lastCommitStat = lastCommitStat == null ? "" : lastCommitStat;
            commitSha = commitSha == null ? "" : commitSha;
            committedFiles = committedFiles == null ? List.of() : List.copyOf(committedFiles);
        }

        private static CommitResult failure(CommitFailureType failureType, String error, String statusText) {
            return new CommitResult(false, failureType, error, statusText, "", "", List.of());
        }
    }
}
