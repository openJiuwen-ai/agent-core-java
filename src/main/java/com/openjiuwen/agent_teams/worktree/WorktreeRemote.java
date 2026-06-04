/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Distributed worktree backend for remote nodes.
 * <p>
 * Enables worktree isolation across machines: the leader sends worktree
 * lifecycle requests via Messager; each remote node maintains its own
 * shallow clone and creates local worktrees within it.
 * <p>
 * Mirrors Python's {@code remote} module in
 * {@code openjiuwen.agent_teams.worktree.remote}.
 */
public class WorktreeRemote {

    private static final Logger logger = Logger.getLogger(WorktreeRemote.class.getName());

    // ── Request / Response models ───────────────────────────────────────

    /**
     * Request sent to a remote node to manage a worktree.
     */
    public static class WorktreeRemoteRequest {
        private String action;  // "create", "remove", or "exists"
        private String slug;
        private String repoUrl;
        private String baseBranch;
        private String worktreePath;
        private WorktreeModels.WorktreeConfig config;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
        public String getBaseBranch() { return baseBranch; }
        public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
        public String getWorktreePath() { return worktreePath; }
        public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }
        public WorktreeModels.WorktreeConfig getConfig() { return config; }
        public void setConfig(WorktreeModels.WorktreeConfig config) { this.config = config; }
    }

    /**
     * Response from a remote node after a worktree operation.
     */
    public static class WorktreeRemoteResponse {
        private boolean success;
        private String worktreePath;
        private String worktreeBranch;
        private String headCommit;
        private boolean existed;
        private boolean exists;
        private String error;

        public static WorktreeRemoteResponse success(String path, String branch, String commit) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.success = true;
            r.worktreePath = path;
            r.worktreeBranch = branch;
            r.headCommit = commit;
            return r;
        }

        public static WorktreeRemoteResponse failure(String error) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.success = false;
            r.error = error;
            return r;
        }

        public static WorktreeRemoteResponse existed(String path) {
            WorktreeRemoteResponse r = new WorktreeRemoteResponse();
            r.existed = true;
            r.worktreePath = path;
            return r;
        }

        public boolean isSuccess() { return success; }
        public String getWorktreePath() { return worktreePath; }
        public String getWorktreeBranch() { return worktreeBranch; }
        public String getHeadCommit() { return headCommit; }
        public boolean getExisted() { return existed; }
        public boolean getExists() { return exists; }
        public String getError() { return error; }
    }

    // ── Remote backend operations ───────────────────────────────────────

    /**
     * Create a worktree on a remote node.
     *
     * @param request Creation request with slug and repo URL
     * @return Response with worktree path and branch
     */
    public CompletableFuture<WorktreeRemoteResponse> createRemoteWorktree(WorktreeRemoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure repo is cloned
                String cloneDir = ensureClone(request.getRepoUrl());
                
                // Create worktree in clone
                String branch = request.getBaseBranch() != null ? request.getBaseBranch() : "main";
                String worktreePath = createWorktreeInClone(cloneDir, request.getSlug(), branch);
                
                logger.info("Created remote worktree: " + worktreePath);
                return WorktreeRemoteResponse.success(worktreePath, branch, getHeadCommit(worktreePath));
            } catch (Exception e) {
                logger.warning("Failed to create remote worktree: " + e.getMessage());
                return WorktreeRemoteResponse.failure(e.getMessage());
            }
        });
    }

    /**
     * Remove a worktree on a remote node.
     *
     * @param request Removal request with worktree path
     * @return Response indicating success or failure
     */
    public CompletableFuture<WorktreeRemoteResponse> removeRemoteWorktree(WorktreeRemoteRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                removeWorktree(request.getWorktreePath());
                logger.info("Removed remote worktree: " + request.getWorktreePath());
                return WorktreeRemoteResponse.success(null, null, null);
            } catch (Exception e) {
                return WorktreeRemoteResponse.failure(e.getMessage());
            }
        });
    }

    /**
     * Check if a worktree exists on a remote node.
     *
     * @param request Query request with worktree path
     * @return Response indicating whether it exists
     */
    public WorktreeRemoteResponse checkWorktreeExists(WorktreeRemoteRequest request) {
        boolean exists = java.nio.file.Files.exists(java.nio.file.Path.of(request.getWorktreePath()));
        WorktreeRemoteResponse r = new WorktreeRemoteResponse();
        r.exists = exists;
        return r;
    }

    // ── Git helpers ───────────────────────────────────────

    private String ensureClone(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }
        Path localPath = Paths.getAgentTeamsHome()
            .resolve("remote_repos")
            .resolve(sha256Prefix(repoUrl, 12));
        if (!Files.isDirectory(localPath.resolve(".git"))) {
            try {
                Files.createDirectories(localPath.getParent());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create remote repo cache", e);
            }
            runGit(Path.of("."), true, "git", "clone", "--depth=1", repoUrl, localPath.toString());
        }
        return localPath.toString();
    }

    private String createWorktreeInClone(String cloneDir, String slug, String branch) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug is required");
        }
        String baseBranch = branch != null && !branch.isBlank() ? branch : "main";
        Path clonePath = Path.of(cloneDir);
        Path worktreePath = Path.of(SlugUtils.worktreePathFor(cloneDir, slug));
        if (Files.exists(worktreePath)) {
            return worktreePath.toString();
        }
        try {
            Files.createDirectories(worktreePath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create worktree parent", e);
        }
        runGit(clonePath, false, "git", "fetch", "origin", baseBranch);
        String ref = resolveRef(clonePath, baseBranch);
        runGit(clonePath, true, "git", "worktree", "add", "-B", SlugUtils.worktreeBranchName(slug), worktreePath.toString(), ref);
        return worktreePath.toString();
    }

    private String getHeadCommit(String worktreePath) {
        return runGit(Path.of(worktreePath), true, "git", "rev-parse", "HEAD").stdout();
    }

    private void removeWorktree(String worktreePath) {
        if (worktreePath == null || worktreePath.isBlank()) {
            throw new IllegalArgumentException("worktreePath is required");
        }
        Path path = Path.of(worktreePath);
        if (!Files.exists(path)) {
            return;
        }
        Path gitRoot = resolveCommonGitRoot(path);
        runGit(gitRoot, true, "git", "worktree", "remove", "--force", path.toString());
        runGit(gitRoot, false, "git", "worktree", "prune");
    }

    private String resolveRef(Path clonePath, String branch) {
        GitResult remote = runGit(clonePath, false, "git", "rev-parse", "--verify", "origin/" + branch);
        if (remote.exitCode() == 0) {
            return "origin/" + branch;
        }
        GitResult local = runGit(clonePath, false, "git", "rev-parse", "--verify", branch);
        return local.exitCode() == 0 ? branch : "HEAD";
    }

    private Path resolveCommonGitRoot(Path worktreePath) {
        GitResult result = runGit(worktreePath, false, "git", "rev-parse", "--git-common-dir");
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            return worktreePath;
        }
        Path common = Path.of(result.stdout());
        if (!common.isAbsolute()) {
            common = worktreePath.resolve(common).normalize();
        }
        if (".git".equals(common.getFileName().toString()) && common.getParent() != null) {
            return common.getParent();
        }
        return common;
    }

    private GitResult runGit(Path cwd, boolean check, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd.toFile());
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (check && exitCode != 0) {
                throw new IllegalStateException(
                    "Git command failed (" + String.join(" ", command) + ") at " + cwd + ": " + stderr
                );
            }
            return new GitResult(exitCode, stdout, stderr);
        } catch (IOException e) {
            if (check) {
                throw new IllegalStateException("Failed to start git command at " + cwd, e);
            }
            return new GitResult(127, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (check) {
                throw new IllegalStateException("Interrupted while running git command at " + cwd, e);
            }
            return new GitResult(130, "", "Interrupted");
        }
    }

    private String sha256Prefix(String value, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, Math.min(length, hex.length()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }
}
