/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Worktree rail base class with lifecycle hooks.
 * <p>
 * Provides hooks for worktree lifecycle events:
 * create, exit, file write, commit, and sync phases.
 * <p>
 * Mirrors Python's {@code WorktreeRail} in
 * {@code openjiuwen.agent_teams.worktree.rails}.
 */
public class WorktreeRails {

    private static final Logger logger = Logger.getLogger(WorktreeRails.class.getName());

    /**
     * Called before worktree creation.
     * <p>
     * Can return a modified slug, or null to proceed unchanged.
     * Raise to abort creation.
     *
     * @param slug      Proposed worktree slug
     * @param repoRoot  Absolute path to the repository root
     * @return Modified slug string, or null to keep original
     */
    public String beforeWorktreeCreate(AgentCallbackContext ctx, String slug, String repoRoot) {
        return null;
    }

    public String beforeWorktreeCreate(String slug, String repoRoot) {
        return beforeWorktreeCreate(null, slug, repoRoot);
    }

    /**
     * Called after worktree creation and post-setup.
     * <p>
     * Use for: dependency installation, setup scripts,
     * environment validation, workspace initialization.
     *
     * @param session The newly created worktree session
     */
    public CompletableFuture<Void> afterWorktreeCreate(AgentCallbackContext ctx, WorktreeSession session) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> afterWorktreeCreate(WorktreeModels.WorktreeSession session) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called before worktree exit.
     * <p>
     * Can return a replacement action, or null to keep the requested action.
     *
     * @param ctx     Agent callback context
     * @param session Current worktree session
     * @param action  Requested action
     * @return replacement action, or null to keep original
     */
    public String beforeWorktreeExit(AgentCallbackContext ctx, WorktreeSession session, String action) {
        return null;
    }

    public boolean beforeWorktreeExit(WorktreeModels.WorktreeSession session) {
        return true;
    }

    /**
     * Called after worktree exit and cleanup.
     *
     * @param slug The slug of the removed worktree
     */
    public void afterWorktreeExit(String slug) {
        // Default: no action
    }

    public CompletableFuture<Void> afterWorktreeExit(
            AgentCallbackContext ctx,
            WorktreeSession session,
            String action
    ) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called before a file is written in the worktree.
     * <p>
     * Use for: access control, write logging, pre-write transforms.
     *
     * @param filePath  Path being written (relative to worktree root)
     * @param content   Content being written
     * @return Modified content, or original to proceed unchanged
     */
    public String beforeFileWrite(String filePath, String content) {
        return content;
    }

    /**
     * Called when agent writes a file in the worktree.
     *
     * @param ctx      Agent callback context
     * @param session  Current worktree session
     * @param filePath Absolute path to the file being written
     * @return true to allow the write, false to block
     */
    public boolean onWorktreeFileWrite(AgentCallbackContext ctx, WorktreeSession session, String filePath) {
        return true;
    }

    /**
     * Called after a file is written in the worktree.
     *
     * @param filePath Path that was written
     */
    public void afterFileWrite(String filePath) {
        // Default: no action
    }

    /**
     * Called before git commit in the worktree.
     * <p>
     * Use for: commit message linting, CI triggers.
     *
     * @param message Proposed commit message
     * @return Modified message, or original to proceed unchanged
     */
    public String beforeCommit(String message) {
        return message;
    }

    /**
     * Called before a commit in the worktree.
     *
     * @param ctx     Agent callback context
     * @param session Current worktree session
     * @param message Proposed commit message
     * @param files   Files to commit
     * @return modified commit message, or null to keep original
     */
    public String beforeWorktreeCommit(
            AgentCallbackContext ctx,
            WorktreeSession session,
            String message,
            List<String> files
    ) {
        return null;
    }

    /**
     * Called after git commit in the worktree.
     *
     * @param message Commit message that was used
     */
    public void afterCommit(String message) {
        // Default: no action
    }

    /**
     * Called after a commit succeeds.
     *
     * @param ctx       Agent callback context
     * @param session   Current worktree session
     * @param commitSha New commit SHA
     * @return completed future
     */
    public CompletableFuture<Void> afterWorktreeCommit(
            AgentCallbackContext ctx,
            WorktreeSession session,
            String commitSha
    ) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Called during worktree-workspace sync for each file.
     * <p>
     * Use for: file filtering, content transforms.
     *
     * @param relativePath Path relative to workspace root
     * @param content      File content
     * @return true to include in sync, false to skip
     */
    public boolean filterSyncFile(String relativePath, String content) {
        return true;
    }

    /**
     * Called when syncing files between worktree and shared workspace.
     *
     * @param ctx       Agent callback context
     * @param session   Current worktree session
     * @param direction push or pull
     * @param files     Relative file paths being synced
     * @return filtered file list
     */
    public List<String> onWorktreeSync(
            AgentCallbackContext ctx,
            WorktreeSession session,
            String direction,
            List<String> files
    ) {
        return files == null ? List.of() : files;
    }

    /**
     * Run setup commands after worktree creation.
     *
     * <p>Mirrors Python's {@code AutoSetupRail} in
     * {@code openjiuwen.agent_teams.worktree.rails}.</p>
     */
    public static class AutoSetupRail extends WorktreeRails {
        private final List<String> commands;

        public AutoSetupRail() {
            this(null);
        }

        public AutoSetupRail(List<String> commands) {
            this.commands = commands == null ? null : List.copyOf(commands);
        }

        @Override
        public CompletableFuture<Void> afterWorktreeCreate(AgentCallbackContext ctx, WorktreeSession session) {
            return CompletableFuture.runAsync(() -> {
                List<String> setupCommands = commands != null ? commands : detectSetup(session.getWorktreePath());
                for (String command : setupCommands) {
                    runSetupCommand(command, session.getWorktreePath());
                }
            });
        }

        public static List<String> detectSetup(String path) {
            Path root = Path.of(path);
            if (Files.exists(root.resolve("pyproject.toml"))) {
                return List.of("uv sync --quiet");
            }
            if (Files.exists(root.resolve("package.json"))) {
                return List.of("npm install --silent");
            }
            return List.of();
        }

        private static void runSetupCommand(String command, String cwd) {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder builder = windows
                    ? new ProcessBuilder("cmd", "/c", command)
                    : new ProcessBuilder("sh", "-c", command);
            builder.directory(Path.of(cwd).toFile());
            try {
                Process process = builder.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    logger.warning("Setup command '" + command + "' failed with exit code " + exitCode);
                }
            } catch (IOException e) {
                logger.warning("Setup command '" + command + "' failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Setup command '" + command + "' interrupted");
            }
        }
    }

    /**
     * Generate a diff summary before keeping a worktree.
     *
     * <p>Mirrors Python's {@code DiffSummaryRail} in
     * {@code openjiuwen.agent_teams.worktree.rails}.</p>
     */
    public static class DiffSummaryRail extends WorktreeRails {
        @Override
        public String beforeWorktreeExit(AgentCallbackContext ctx, WorktreeSession session, String action) {
            if (!"keep".equals(action)) {
                return null;
            }
            String base = session.getOriginalHeadCommit();
            if (base == null || base.isBlank()) {
                return null;
            }
            Git.GitResult diff = Git.runGit(
                    List.of("diff", "--stat", base + "..HEAD"),
                    session.getWorktreePath()
            ).join();
            if (diff.ok() && diff.stdout() != null && !diff.stdout().isBlank()) {
                logger.info("Worktree '" + session.getWorktreeName() + "' diff summary:\n" + diff.stdout());
            }
            return null;
        }
    }
}
