/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import com.openjiuwen.autoharness.infra.GitOperations;
import examples.gitcode_issue_evolver.RepositoryCoordinates;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pushes only an already-committed issue branch to the configured publication repository.
 *
 * @since 0.1.12
 */
public final class GitCodeForkPushGateway implements ForkPushGateway {
    private static final Pattern BRANCH_PATTERN =
            Pattern.compile("auto-evolving/issue-[1-9][0-9]*-[a-z0-9-]+");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
    private final RepositoryCoordinates coordinates;
    private final String token;
    private final GitPushOperationsFactory operationsFactory;

    /**
     * Create the privileged push boundary.
     *
     * @param coordinates validated target and publication repositories
     * @param botOwner compatibility owner that must match the publication repository owner
     * @param token robot token injected only into Git subprocesses
     */
    public GitCodeForkPushGateway(RepositoryCoordinates coordinates, String botOwner, String token) {
        this(coordinates, botOwner, token, productionFactory(coordinates, botOwner, token));
    }

    GitCodeForkPushGateway(RepositoryCoordinates coordinates, String botOwner, String token,
                           GitPushOperationsFactory operationsFactory) {
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
        String requiredBotOwner = requireText(botOwner, "bot owner");
        this.token = requireText(token, "GitCode token");
        this.operationsFactory = Objects.requireNonNull(operationsFactory,
                "operationsFactory must not be null");
        if (!requiredBotOwner.equals(this.coordinates.publishOwner())) {
            throw new IllegalArgumentException("bot owner must match the publication repository owner");
        }
    }

    @Override
    public PushResult push(Path worktree, String branch, String expectedHeadSha) {
        Path root = worktree == null ? null : worktree.toAbsolutePath().normalize();
        if (root == null || !Files.isDirectory(root)) {
            return new PushResult(false, "", "worktree does not exist");
        }
        if (branch == null || !BRANCH_PATTERN.matcher(branch).matches()) {
            return new PushResult(false, "", "invalid automatic branch name");
        }
        if (expectedHeadSha == null || !SHA_PATTERN.matcher(expectedHeadSha).matches()) {
            return new PushResult(false, "", "expected commit SHA is invalid");
        }
        GitPushOperations git = operationsFactory.create(root, coordinates.publishCloneUri());
        String currentBranch = git.currentBranch();
        if (!branch.equals(currentBranch)) {
            return new PushResult(false, "", "current branch does not match publication branch");
        }
        String currentHead = git.currentHead();
        if (!expectedHeadSha.equalsIgnoreCase(currentHead)) {
            return new PushResult(false, currentHead, "HEAD changed after verification");
        }
        GitPushResult result = git.pushBranch(branch);
        String error = result.success() ? "" : redact(result.error());
        return new PushResult(result.success(), currentHead, error);
    }

    private String redact(String error) {
        return error == null ? "" : error.replace(token, "[REDACTED]");
    }

    private static GitPushOperationsFactory productionFactory(RepositoryCoordinates coordinates,
                                                               String botOwner, String token) {
        RepositoryCoordinates requiredCoordinates = Objects.requireNonNull(
                coordinates, "coordinates must not be null");
        String requiredOwner = requireText(botOwner, "bot owner");
        String requiredToken = requireText(token, "GitCode token");
        return (worktree, remoteUri) -> new GitPushOperations() {
            private final GitOperations git = new GitOperations(
                    worktree.toString(), remoteUri.toString(), requiredCoordinates.baseBranch(),
                    requiredCoordinates.publishOwner(), requiredCoordinates.targetOwner(),
                    requiredCoordinates.targetName(), requiredOwner, requiredToken, "", "");

            @Override
            public String currentBranch() {
                return git.currentBranch();
            }

            @Override
            public String currentHead() {
                return git.currentHead();
            }

            @Override
            public GitPushResult pushBranch(String branch) {
                Map<String, Object> result = git.push(branch);
                boolean success = Boolean.TRUE.equals(result.get("success"));
                Object output = result.get("output");
                return new GitPushResult(success, output == null ? "" : output.toString());
            }
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    @FunctionalInterface
    interface GitPushOperationsFactory {
        /**
         * Create Git operations for one validated Worktree and publication URI.
         *
         * @param worktree isolated Worktree root
         * @param remoteUri fixed-host publication URI
         * @return restricted Git operations
         */
        GitPushOperations create(Path worktree, URI remoteUri);
    }

    interface GitPushOperations {
        /**
         * Read the checked-out branch.
         *
         * @return branch name
         */
        String currentBranch();

        /**
         * Read the checked-out commit.
         *
         * @return commit SHA
         */
        String currentHead();

        /**
         * Push the exact validated branch.
         *
         * @param branch branch name
         * @return push result
         */
        GitPushResult pushBranch(String branch);
    }

    record GitPushResult(boolean success, String error) {
    }
}
