/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.openjiuwen.agent_teams.schema.TeamEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Team workspace rail for transparent version control and locking.
 * <p>
 * Intercepts standard filesystem tool calls targeting the .team/ mount point
 * and applies workspace policies (lock checking, auto-commit, push) without
 * the agent needing special workspace APIs.
 * <p>
 * Agent uses standard read_file/write_file — this rail adds behavior.
 * <p>
 * Mirrors Python's {@code TeamWorkspaceRail} in
 * {@code openjiuwen.agent_teams.team_workspace.rails}.
 */
public class WorkspaceRails {

    private static final Logger logger = Logger.getLogger(WorkspaceRails.class.getName());

    public static final String TEAM_PREFIX = ".team/";
    public static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");
    public static final Set<String> READ_TOOLS = Set.of("read_file", "glob", "grep", "list_files");
    private static final ThreadLocal<String> TEAM_WORKSPACE_CONTEXT = new ThreadLocal<>();

    private final WorkspaceManager workspaceManager;
    private final String memberName;
    private double lastPullTime;
    private final double pullInterval;

    /**
     * Create WorkspaceRails.
     *
     * @param workspaceManager Workspace manager instance
     * @param memberName       Member name for lock checks
     */
    public WorkspaceRails(WorkspaceManager workspaceManager, String memberName) {
        this.workspaceManager = workspaceManager;
        this.memberName = memberName;
        this.lastPullTime = 0.0;
        this.pullInterval = 5.0;
    }

    /**
     * Initialize: populate team_workspace on the agent's CwdState.
     */
    public void init(Object agent) {
        // Set team workspace path in context
        setTeamWorkspace(workspaceManager.getWorkspacePath());
    }

    /**
     * Before file operations on .team/: pull for reads, check lock for writes.
     *
     * @param toolName Tool name being invoked
     * @param filePath Target file path
     * @param extra    Extra context map for storing rejection info
     */
    public CompletableFuture<Void> beforeToolCall(String toolName, String filePath, Map<String, Object> extra) {
        if (filePath == null || !filePath.startsWith(TEAM_PREFIX)) {
            return CompletableFuture.completedFuture(null);
        }

        // Read path: pull before read (distributed mode, throttled)
        if (READ_TOOLS.contains(toolName)) {
            return maybePull();
        }

        if (!WRITE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(null);
        }

        // Write path: pull + lock check
        return maybePull().thenRun(() -> {
            if (workspaceManager.getConfig().getConflictStrategy() == WorkspaceManager.ConflictStrategy.LOCK) {
                WorkspaceManager.WorkspaceFileLock lock = workspaceManager.getLock(filePath);
                if (lock != null && !lock.getHolderId().equals(memberName) && !lock.isExpired()) {
                    String msg = "File '" + filePath + "' is locked by " + 
                        lock.getHolderName() + " (" + lock.getHolderId() + ")";
                    logger.warning(msg);
                    if (extra != null) {
                        extra.put("workspace_lock_rejected", msg);
                    }
                }
            }
        });
    }

    /**
     * After write/edit to .team/: git commit (+ push) + publish event.
     *
     * @param toolName Tool name being invoked
     * @param filePath Target file path
     */
    public CompletableFuture<Void> afterToolCall(String toolName, String filePath) {
        if (!WRITE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(null);
        }

        if (filePath == null || !filePath.startsWith(TEAM_PREFIX)) {
            return CompletableFuture.completedFuture(null);
        }

        String realPath = resolveWorkspaceRelative(filePath);

        // Auto version control
        if (workspaceManager.getConfig().isVersionControl()) {
            return workspaceManager.autoCommit(realPath, memberName)
                .thenRun(() -> publishArtifactEvent(realPath));
        }

        publishArtifactEvent(realPath);
        return CompletableFuture.completedFuture(null);
    }

    // ── Internal helpers ───────────────────────────────────────

    private CompletableFuture<Void> maybePull() {
        if (!workspaceManager.getConfig().isVersionControl()) {
            return CompletableFuture.completedFuture(null);
        }
        if (workspaceManager.getMode() != WorkspaceManager.WorkspaceMode.DISTRIBUTED) {
            return CompletableFuture.completedFuture(null);
        }
        double now = System.currentTimeMillis() / 1000.0;
        if (now - lastPullTime < pullInterval) {
            return CompletableFuture.completedFuture(null);
        }
        lastPullTime = now;
        return runGitPull(workspaceManager.getWorkspacePath());
    }

    private String resolveWorkspaceRelative(String path) {
        if (path.startsWith(TEAM_PREFIX)) {
            String afterPrefix = path.substring(TEAM_PREFIX.length());
            String teamNamePrefix = workspaceManager.getTeamName() + "/";
            if (afterPrefix.startsWith(teamNamePrefix)) {
                return afterPrefix.substring(teamNamePrefix.length());
            }
            return afterPrefix;
        }
        return path;
    }

    private void publishArtifactEvent(String artifactPath) {
        if (workspaceManager != null && workspaceManager.getEventPublisher() != null) {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put("team_name", workspaceManager.getTeamName());
            event.put("member_name", memberName);
            event.put("artifact_path", artifactPath);
            workspaceManager.getEventPublisher().publishEvent(TeamEvent.WORKSPACE_ARTIFACT_UPDATED, event);
        }
    }

    private void setTeamWorkspace(String path) {
        if (path == null || path.isBlank()) {
            TEAM_WORKSPACE_CONTEXT.remove();
        } else {
            TEAM_WORKSPACE_CONTEXT.set(path);
        }
    }

    public static String getTeamWorkspace() {
        return TEAM_WORKSPACE_CONTEXT.get();
    }

    public static void clearTeamWorkspace() {
        TEAM_WORKSPACE_CONTEXT.remove();
    }

    private CompletableFuture<Void> runGitPull(String cwd) {
        return CompletableFuture.runAsync(() -> {
            try {
                GitResult result = runCommand(Path.of(cwd), "git", "pull", "--rebase", "--autostash", "origin", "main");
                if (result.exitCode() != 0) {
                    logger.warning("Workspace pull failed: " + result.stderr());
                }
            } catch (Exception e) {
                logger.warning("Workspace pull failed: " + e.getMessage());
            }
        });
    }

    private GitResult runCommand(Path cwd, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        return new GitResult(exitCode, stdout, stderr);
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }
}
