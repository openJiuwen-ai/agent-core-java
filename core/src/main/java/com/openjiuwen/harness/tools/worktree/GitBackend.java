/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code GitBackend} in
 * {@code openjiuwen/harness/tools/worktree/backend.py}.
 */
public final class GitBackend implements WorktreeBackend {

    private final WorktreeConfig config;

    public GitBackend() {
        this(null);
    }

    public GitBackend(WorktreeConfig config) {
        this.config = config == null ? new WorktreeConfig() : config;
    }

    @Override
    public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
        return CompletableFuture.supplyAsync(() -> {
            String worktreeBranch = SlugUtils.worktreeBranchName(slug);
            String existingHead = Git.readWorktreeHeadSha(targetPath).join();
            if (existingHead != null) {
                return new WorktreeCreateResult(targetPath, worktreeBranch, existingHead, null, true, false);
            }

            try {
                Path parent = Path.of(targetPath).getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }

            BaseResolution base = resolveBase(repoRoot);
            List<String> sparsePaths = config.getSparsePaths();
            boolean useSparseCheckout = sparsePaths != null && !sparsePaths.isEmpty();
            Git.worktreeAdd(repoRoot, targetPath, worktreeBranch, base.baseRef(), useSparseCheckout).join();

            if (useSparseCheckout) {
                try {
                    Git.sparseCheckoutSet(targetPath, sparsePaths).join();
                } catch (CompletionException exception) {
                    Git.worktreeRemove(targetPath, repoRoot, true).join();
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    if (cause instanceof Git.GitError gitError) {
                        throw new Git.GitError(
                                List.of("sparse-checkout"),
                                gitError.getReturncode(),
                                "Failed sparse checkout, worktree cleaned up: " + gitError.getStderr()
                        );
                    }
                    throw exception;
                }
            }

            String headCommit = base.sha();
            if (headCommit == null) {
                headCommit = Git.revParse("HEAD", targetPath).join();
            }
            return new WorktreeCreateResult(targetPath, worktreeBranch, headCommit, base.baseRef(), false, false);
        });
    }

    @Override
    public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
        if (!Files.exists(Path.of(worktreePath))) {
            return CompletableFuture.failedFuture(new NoSuchFileException(worktreePath));
        }
        return CompletableFuture.supplyAsync(() -> {
            String branch = Git.getCurrentBranch(worktreePath).join();
            boolean removed = Git.worktreeRemove(worktreePath, repoRoot, true).join();
            if (removed && branch != null && branch.startsWith("worktree-")) {
                Git.branchDelete(branch, repoRoot).join();
            }
            return removed;
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String worktreePath) {
        return Git.readWorktreeHeadSha(worktreePath).thenApply(head -> head != null);
    }

    private static BaseResolution resolveBase(String repoRoot) {
        String defaultBranch = Git.getDefaultBranch(repoRoot).join();
        String originRef = "origin/" + defaultBranch;

        String sha = Git.revParse(originRef, repoRoot).join();
        if (sha != null) {
            return new BaseResolution(originRef, sha);
        }

        Boolean fetched = Git.fetchRef(repoRoot, defaultBranch).join();
        if (Boolean.TRUE.equals(fetched)) {
            sha = Git.revParse(originRef, repoRoot).join();
            return new BaseResolution(originRef, sha);
        }

        sha = Git.revParse("HEAD", repoRoot).join();
        return new BaseResolution("HEAD", sha);
    }

    private record BaseResolution(String baseRef, String sha) {
    }
}
