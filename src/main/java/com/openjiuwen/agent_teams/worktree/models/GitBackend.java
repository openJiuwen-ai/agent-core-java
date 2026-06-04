/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree.models;

import com.openjiuwen.agent_teams.worktree.Git;
import com.openjiuwen.agent_teams.worktree.SlugUtils;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeCreateResult;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Git backend for worktree operations.
 * <p>
 * Mirrors Python's {@code GitBackend} in {@code openjiuwen.agent_teams.worktree.backend}.
 * Provides git worktree create/remove operations.
 */
public class GitBackend implements WorktreeBackend {

    private final WorktreeConfig config;

    public GitBackend() {
        this(new WorktreeConfig());
    }

    public GitBackend(WorktreeConfig config) {
        this.config = config != null ? config : new WorktreeConfig();
    }

    public WorktreeConfig getConfig() {
        return config;
    }

    @Override
    public CompletableFuture<WorktreeCreateResult> create(String slug, String repoPath, String targetPath) {
        return CompletableFuture.supplyAsync(() -> {
            String branch = SlugUtils.worktreeBranchName(slug);
            String existingHead = Git.readWorktreeHeadSha(targetPath).join();
            if (existingHead != null) {
                return new WorktreeCreateResult(targetPath, branch, existingHead, null, true, false);
            }

            Path parent = Path.of(targetPath).getParent();
            try {
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create worktree parent directory", e);
            }

            String currentBranch = Git.getCurrentBranch(repoPath).join();
            String baseRef = currentBranch != null ? currentBranch : "HEAD";
            String baseSha = Git.revParse("HEAD", repoPath).join();
            boolean sparse = config.getSparsePaths() != null && !config.getSparsePaths().isEmpty();

            Git.worktreeAdd(repoPath, targetPath, branch, baseRef, sparse).join();
            if (sparse) {
                Git.sparseCheckoutSet(targetPath, config.getSparsePaths()).join();
            }
            if (baseSha == null) {
                baseSha = Git.revParse("HEAD", targetPath).join();
            }

            return new WorktreeCreateResult(targetPath, branch, baseSha, baseRef, false, false);
        });
    }

    @Override
    public CompletableFuture<Boolean> remove(String worktreePath, String repoPath) {
        if (!Files.exists(Path.of(worktreePath))) {
            return CompletableFuture.failedFuture(new FileNotFoundException(worktreePath));
        }
        return CompletableFuture.supplyAsync(() -> {
            String branch = Git.getCurrentBranch(worktreePath).join();
            boolean ok = Git.worktreeRemove(worktreePath, repoPath, true).join();
            if (ok && branch != null && branch.startsWith("worktree-")) {
                Git.branchDelete(branch, repoPath).join();
            }
            return ok;
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String worktreePath) {
        return Git.readWorktreeHeadSha(worktreePath).thenApply(head -> head != null);
    }
}
