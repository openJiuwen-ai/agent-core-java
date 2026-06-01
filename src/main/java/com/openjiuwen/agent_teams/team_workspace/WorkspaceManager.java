/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Team shared workspace manager.
 * <p>
 * Handles locking, versioning, sync, and conflict detection for the team
 * shared workspace directory. File I/O is delegated to SysOperation tools
 * via the .team/ symlink mount — this module manages only metadata and
 * version control.
 * <p>
 * Two operating modes:
 * - LOCAL: single _team_workspace/ directory, symlink mount, in-memory locks.
 * - DISTRIBUTED: per-node clone, git push/pull sync, leader-coordinated locks
 *   (Phase 3).
 * <p>
 * Mirrors Python's {@code TeamWorkspaceManager} in
 * {@code openjiuwen.agent_teams.team_workspace.manager}.
 */
public class WorkspaceManager {

    private static final Logger logger = Logger.getLogger(WorkspaceManager.class.getName());
    public static final int ERROR_PRIVILEGE_NOT_HELD = 1314;

    private final TeamWorkspaceConfig config;
    private final String workspacePath;
    private final String teamName;
    private final WorkspaceMode mode;
    private final EventPublisher publishEvent;

    // Local lock state
    private final Map<String, WorkspaceFileLock> locks;
    private final ReentrantLock lockMutex;

    // Distributed coordination
    private final Object messager;
    private final String leaderId;
    private final String nodeId;

    /**
     * Create WorkspaceManager.
     *
     * @param config        Workspace configuration
     * @param workspacePath Path to workspace directory
     * @param teamName      Team name
     * @param mode          LOCAL or DISTRIBUTED mode
     * @param messager      Optional messager for distributed coordination
     * @param leaderId      Leader node identifier
     * @param nodeId        Current node identifier
     * @param publishEvent  Event publisher callback
     */
    public WorkspaceManager(
            TeamWorkspaceConfig config,
            String workspacePath,
            String teamName,
            WorkspaceMode mode,
            Object messager,
            String leaderId,
            String nodeId,
            EventPublisher publishEvent) {
        this.config = config;
        this.workspacePath = workspacePath;
        this.teamName = teamName;
        this.mode = mode;
        this.publishEvent = publishEvent;
        this.messager = messager;
        this.leaderId = leaderId;
        this.nodeId = nodeId;
        this.locks = new HashMap<>();
        this.lockMutex = new ReentrantLock();
    }

    // ── Initialization ───────────────────────────────────────

    /**
     * Initialize workspace directory and git repo.
     *
     * @param remoteUrl Git remote URL for distributed workspace repo
     */
    public CompletableFuture<Void> initialize() {
        return initialize(null);
    }

    public CompletableFuture<Void> initialize(String remoteUrl) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path wsPath = Path.of(workspacePath);
                Files.createDirectories(wsPath);

                // Create artifact directories
                for (String dir : config.getArtifactDirs()) {
                    Files.createDirectories(wsPath.resolve(dir));
                }

                // Create skills directory
                Files.createDirectories(wsPath.resolve("skills"));

                if (!config.isVersionControl()) {
                    logger.info("Workspace " + workspacePath + 
                        " initialized as plain shared directory (version_control disabled)");
                    return;
                }

                Path gitDir = wsPath.resolve(".git");
                if (Files.isDirectory(gitDir)) {
                    logger.fine("Workspace already initialized at " + workspacePath);
                    return;
                }

                if (mode == WorkspaceMode.DISTRIBUTED && remoteUrl != null && 
                    !leaderId.equals(nodeId)) {
                    // Remote node: clone the workspace repo
                    runGitClone(remoteUrl, wsPath);
                    logger.info("Cloned workspace repo from " + remoteUrl);
                } else {
                    // Leader or LOCAL: init fresh repo
                    runGitInit(wsPath);
                    runGitEmptyCommit(wsPath, "Initialize team workspace");
                    if (remoteUrl != null) {
                        runGitAddRemote(wsPath, remoteUrl);
                    }
                    logger.info("Initialized workspace git repo at " + workspacePath);
                }
            } catch (Exception e) {
                logger.warning("Failed to initialize workspace: " + e.getMessage());
            }
        });
    }

    /**
     * Mount the shared team workspace into an agent workspace at
     * {@code .team/{teamName}}.
     */
    public void mountIntoWorkspace(String workspaceRoot) {
        try {
            Path teamDir = Path.of(workspaceRoot, ".team");
            Files.createDirectories(teamDir);
            Path linkPath = teamDir.resolve(teamName);
            if (!Files.exists(linkPath)) {
                mountDirectory(Path.of(workspacePath), linkPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to mount team workspace", e);
        }
    }

    private void mountDirectory(Path targetPath, Path linkPath) throws IOException {
        try {
            Files.createSymbolicLink(linkPath, targetPath);
        } catch (UnsupportedOperationException symlinkError) {
            Files.createDirectories(linkPath);
        } catch (FileSystemException symlinkError) {
            if (!isWindowsPrivilegeFailure(symlinkError)) {
                throw symlinkError;
            }
            Files.createDirectories(linkPath);
        }
    }

    private boolean isWindowsPrivilegeFailure(FileSystemException error) {
        if (error instanceof AccessDeniedException) {
            return true;
        }
        String reason = error.getReason();
        String message = error.getMessage();
        return containsPrivilegeMarker(reason) || containsPrivilegeMarker(message);
    }

    private boolean containsPrivilegeMarker(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("privilege") || text.contains("\u7279\u6743");
    }

    /**
     * Pull latest changes in distributed mode.
     */
    public CompletableFuture<Boolean> pull() {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
                return false;
            }
            GitResult result = runGit(
                Path.of(workspacePath),
                false,
                "git",
                "pull",
                "--rebase",
                "--autostash",
                "origin",
                "main"
            );
            return result.exitCode() == 0 && !result.stdout().contains("Already up to date");
        });
    }

    /**
     * Push local commits in distributed mode.
     */
    public CompletableFuture<Boolean> push() {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
                return true;
            }
            GitResult result = runGit(Path.of(workspacePath), false, "git", "push", "origin", "main");
            if (result.exitCode() != 0) {
                logger.warning("Workspace push failed: " + result.stderr());
            }
            return result.exitCode() == 0;
        });
    }

    // ── Lock Management ───────────────────────────────────────

    /**
     * Acquire a lock on a file path.
     *
     * @param path        File path to lock
     * @param memberId    Member identifier requesting the lock
     * @param memberName  Display name of member
     * @return true if lock acquired successfully
     */
    public CompletableFuture<Boolean> acquireLock(String path, String memberId, String memberName) {
        return acquireLock(path, memberId, memberName, 300);
    }

    /**
     * Acquire a lock on a file path with a custom timeout.
     *
     * @param path           File path to lock
     * @param memberId       Member identifier requesting the lock
     * @param memberName     Display name of member
     * @param timeoutSeconds Lock timeout in seconds
     * @return true if lock acquired successfully
     */
    public CompletableFuture<Boolean> acquireLock(
            String path,
            String memberId,
            String memberName,
            int timeoutSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            lockMutex.lock();
            try {
                WorkspaceFileLock existing = locks.get(path);
                if (existing != null && !existing.isExpired()) {
                    if (!existing.getHolderId().equals(memberId)) {
                        return false;  // Locked by another member
                    }
                } else if (existing != null) {
                    locks.remove(path);
                }
                WorkspaceFileLock newLock = new WorkspaceFileLock(
                    path, memberId, memberName, System.currentTimeMillis(), timeoutSeconds
                );
                locks.put(path, newLock);
                logger.info("Lock acquired on " + path + " by " + memberName);
                return true;
            } finally {
                lockMutex.unlock();
            }
        });
    }

    /**
     * Release a lock on a file path.
     *
     * @param path     File path to unlock
     * @param memberId Member releasing the lock
     * @return true if lock was released
     */
    public boolean releaseLock(String path, String memberId) {
        lockMutex.lock();
        try {
            WorkspaceFileLock lock = locks.get(path);
            if (lock != null && lock.getHolderId().equals(memberId)) {
                locks.remove(path);
                logger.info("Lock released on " + path + " by " + memberId);
                return true;
            }
            return false;
        } finally {
            lockMutex.unlock();
        }
    }

    /**
     * Get current lock on a path.
     *
     * @param path File path
     * @return Current lock or null
     */
    public WorkspaceFileLock getLock(String path) {
        lockMutex.lock();
        try {
            WorkspaceFileLock lock = locks.get(path);
            if (lock != null && lock.isExpired()) {
                locks.remove(path);
                return null;
            }
            return lock;
        } finally {
            lockMutex.unlock();
        }
    }

    /**
     * List all active locks.
     */
    public List<WorkspaceFileLock> listLocks() {
        lockMutex.lock();
        try {
            locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
            return List.copyOf(locks.values());
        } finally {
            lockMutex.unlock();
        }
    }

    // ── Version Control ───────────────────────────────────────

    /**
     * Auto-commit a file after write.
     *
     * @param relativePath Relative path in workspace
     * @param memberName   Member making the change
     */
    public CompletableFuture<Void> autoCommit(String relativePath, String memberName) {
        return CompletableFuture.runAsync(() -> {
            if (!config.isVersionControl()) {
                return;
            }
            try {
                runGitAdd(workspacePath, relativePath);
                if (!hasStagedChanges(workspacePath)) {
                    return;
                }
                runGitCommit(workspacePath, "Update " + relativePath + " by " + memberName);
                if (mode == WorkspaceMode.DISTRIBUTED) {
                    runGitPush(workspacePath);
                }
            } catch (Exception e) {
                logger.warning("Auto-commit failed: " + e.getMessage());
            }
        });
    }

    /**
     * Get commit history for a file.
     *
     * @param path File path
     * @return List of commit info
     */
    public CompletableFuture<List<Map<String, Object>>> getHistory(String path) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isVersionControl()) {
                return List.of();
            }
            return runGitLog(workspacePath, path);
        });
    }

    // ── Git helpers ───────────────────────────────────────

    private void runGitInit(Path cwd) {
        runGit(cwd, true, "git", "init");
    }

    private void runGitEmptyCommit(Path cwd, String message) {
        runGit(cwd, true, "git", "commit", "--allow-empty", "-m", message);
    }

    private void runGitAddRemote(Path cwd, String remoteUrl) {
        GitResult existing = runGit(cwd, false, "git", "remote", "get-url", "origin");
        if (existing.exitCode() == 0) {
            return;
        }
        runGit(cwd, true, "git", "remote", "add", "origin", remoteUrl);
    }

    private void runGitClone(String remoteUrl, Path target) {
        Path parent = target.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create clone parent " + parent, e);
            }
        }
        runGit(parent != null ? parent : Path.of("."), true, "git", "clone", remoteUrl, target.toString());
    }

    private void runGitAdd(String cwd, String path) {
        runGit(Path.of(cwd), true, "git", "add", "--", path);
    }

    private void runGitCommit(String cwd, String message) {
        runGit(Path.of(cwd), true, "git", "commit", "-m", message);
    }

    private void runGitPush(String cwd) {
        GitResult result = runGit(Path.of(cwd), false, "git", "push", "origin", "main");
        if (result.exitCode() != 0) {
            logger.warning("Workspace push failed: " + result.stderr());
        }
    }

    private List<Map<String, Object>> runGitLog(String cwd, String path) {
        GitResult result = runGit(
            Path.of(cwd),
            false,
            "git",
            "log",
            "--max-count=10",
            "--format=%H|%an|%ai|%s",
            "--",
            path
        );
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            return List.of();
        }
        return result.stdout().lines()
            .map(line -> line.split("\\|", 4))
            .filter(parts -> parts.length == 4)
            .map(parts -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("commit", parts[0]);
                item.put("author", parts[1]);
                item.put("date", parts[2]);
                item.put("message", parts[3]);
                return item;
            })
            .toList();
    }

    private boolean hasStagedChanges(String cwd) {
        GitResult result = runGit(Path.of(cwd), false, "git", "diff", "--cached", "--quiet");
        return result.exitCode() == 1;
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

    private record GitResult(int exitCode, String stdout, String stderr) {
    }

    // ── Getters ───────────────────────────────────────

    public String getWorkspacePath() { return workspacePath; }
    public String getTeamName() { return teamName; }
    public WorkspaceMode getMode() { return mode; }
    public TeamWorkspaceConfig getConfig() { return config; }
    public EventPublisher getEventPublisher() { return publishEvent; }

    // ── Inner classes ───────────────────────────────────────

    /**
     * Event publisher callback interface.
     */
    public interface EventPublisher {
        void publishEvent(String eventType, Object event);
    }

    /**
     * Workspace mode enum.
     */
    public enum WorkspaceMode {
        LOCAL,
        DISTRIBUTED
    }

    /**
     * Workspace configuration.
     */
    public static class TeamWorkspaceConfig {
        private boolean enabled;
        private String rootPath;
        private List<String> artifactDirs = new ArrayList<>(List.of(
            "artifacts/code",
            "artifacts/docs",
            "artifacts/reports",
            "trajectories"
        ));
        private boolean versionControl = true;
        private ConflictStrategy conflictStrategy = ConflictStrategy.LOCK;
        private String remoteUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public List<String> getArtifactDirs() { return new ArrayList<>(artifactDirs); }
        public void setArtifactDirs(List<String> artifactDirs) {
            this.artifactDirs = artifactDirs != null ? new ArrayList<>(artifactDirs) : new ArrayList<>();
        }
        public boolean isVersionControl() { return versionControl; }
        public void setVersionControl(boolean versionControl) { this.versionControl = versionControl; }
        public ConflictStrategy getConflictStrategy() { return conflictStrategy; }
        public void setConflictStrategy(ConflictStrategy conflictStrategy) {
            this.conflictStrategy = conflictStrategy != null ? conflictStrategy : ConflictStrategy.LOCK;
        }
        public String getRemoteUrl() { return remoteUrl; }
        public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }
    }

    /**
     * Conflict strategy enum.
     */
    public enum ConflictStrategy {
        LOCK,
        OVERWRITE,
        MERGE,
        LAST_WRITE_WINS
    }

    /**
     * File lock state.
     */
    public static class WorkspaceFileLock {
        private final String path;
        private final String holderId;
        private final String holderName;
        private final long acquiredAt;
        private final int timeoutSeconds;

        public WorkspaceFileLock(String path, String holderId, String holderName, long acquiredAt) {
            this(path, holderId, holderName, acquiredAt, 300);
        }

        public WorkspaceFileLock(String path, String holderId, String holderName, long acquiredAt, int timeoutSeconds) {
            this.path = path;
            this.holderId = holderId;
            this.holderName = holderName;
            this.acquiredAt = acquiredAt;
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getPath() { return path; }
        public String getHolderId() { return holderId; }
        public String getHolderName() { return holderName; }
        public long getAcquiredAt() { return acquiredAt; }
        public int getTimeoutSeconds() { return timeoutSeconds; }

        public boolean isExpired() {
            long ttlMillis = Math.max(0L, timeoutSeconds) * 1000L;
            return System.currentTimeMillis() > acquiredAt + ttlMillis;
        }
    }
}
