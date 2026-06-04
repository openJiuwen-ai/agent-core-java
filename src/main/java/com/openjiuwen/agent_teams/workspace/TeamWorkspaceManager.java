/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal shared workspace manager.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceManager} in
 * {@code openjiuwen.agent_teams.team_workspace.manager}.</p>
 */
public class TeamWorkspaceManager {

    private final TeamWorkspaceConfig config;
    private final String workspacePath;
    private final String teamName;
    private final WorkspaceMode mode;
    private final Map<String, WorkspaceFileLock> locks = new LinkedHashMap<>();

    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName) {
        this(config, workspacePath, teamName, WorkspaceMode.LOCAL);
    }

    public TeamWorkspaceManager(TeamWorkspaceConfig config, String workspacePath, String teamName, WorkspaceMode mode) {
        this.config = config != null ? config : new TeamWorkspaceConfig();
        this.workspacePath = workspacePath;
        this.teamName = teamName;
        this.mode = mode != null ? mode : WorkspaceMode.LOCAL;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public String getTeamName() {
        return teamName;
    }

    public WorkspaceMode getMode() {
        return mode;
    }

    public void initialize() {
        initialize(config.getRemoteUrl());
    }

    public void initialize(String remoteUrl) {
        try {
            Path root = Path.of(workspacePath);
            Files.createDirectories(root);
            for (String directory : config.getArtifactDirs()) {
                Files.createDirectories(root.resolve(directory));
            }
            Files.createDirectories(root.resolve("skills"));

            if (!config.isVersionControl()) {
                return;
            }
            if (Files.isDirectory(root.resolve(".git"))) {
                return;
            }

            if (mode == WorkspaceMode.DISTRIBUTED && remoteUrl != null && !remoteUrl.isBlank()) {
                CommandResult clone = runCommand(root.getParent() != null ? root.getParent() : Path.of("."),
                        false, "git", "clone", remoteUrl, root.getFileName().toString());
                if (clone.exitCode == 0) {
                    return;
                }
            }
            runCommand(root, true, "git", "init");
            runCommand(root, true, "git", "commit", "--allow-empty", "-m", "Initialize team workspace");
            if (mode == WorkspaceMode.DISTRIBUTED && remoteUrl != null && !remoteUrl.isBlank()) {
                CommandResult remote = runCommand(root, false, "git", "remote", "get-url", "origin");
                if (remote.exitCode != 0) {
                    runCommand(root, true, "git", "remote", "add", "origin", remoteUrl);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to initialize team workspace", error);
        }
    }

    public void mountIntoWorkspace(String workspaceRoot) {
        try {
            Path teamDir = Path.of(workspaceRoot, ".team");
            Files.createDirectories(teamDir);
            Path link = teamDir.resolve(teamName);
            if (!Files.exists(link)) {
                createDirectoryLink(Path.of(workspacePath), link);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to mount team workspace", e);
        }
    }

    public void mountIntoWorktree(String worktreePath) {
        try {
            Path worktree = Path.of(worktreePath);
            Files.createDirectories(worktree);
            Path link = worktree.resolve(".team");
            if (!Files.exists(link)) {
                createDirectoryLink(Path.of(workspacePath), link);
            }

            Path gitignore = worktree.resolve(".gitignore");
            String existing = Files.exists(gitignore) ? Files.readString(gitignore) : "";
            List<String> additions = new ArrayList<>();
            if (!existing.contains(".agent/")) {
                additions.add(".agent/");
            }
            if (!existing.contains(".team/")) {
                additions.add(".team/");
            }
            if (!additions.isEmpty()) {
                StringBuilder updated = new StringBuilder(existing);
                if (!existing.isEmpty() && !existing.endsWith("\n")) {
                    updated.append(System.lineSeparator());
                }
                updated.append("# Agent Teams managed").append(System.lineSeparator());
                for (String addition : additions) {
                    updated.append(addition).append(System.lineSeparator());
                }
                Files.writeString(gitignore, updated.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to mount team workspace into worktree", error);
        }
    }

    public boolean pull() {
        if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
            return false;
        }
        CommandResult result = runCommand(Path.of(workspacePath), false, "git", "pull", "--rebase", "--autostash",
                "origin", "main");
        return result.exitCode == 0 && !result.stdout.contains("Already up to date");
    }

    public boolean push() {
        if (!config.isVersionControl() || mode != WorkspaceMode.DISTRIBUTED) {
            return true;
        }
        return runCommand(Path.of(workspacePath), false, "git", "push", "origin", "main").exitCode == 0;
    }

    public String autoCommit(String relativePath, String memberName) {
        if (!config.isVersionControl()) {
            return null;
        }
        Path root = Path.of(workspacePath);
        runCommand(root, false, "git", "add", "--", relativePath);
        if (runCommand(root, false, "git", "diff", "--cached", "--quiet").exitCode == 0) {
            return null;
        }
        CommandResult commit = runCommand(root, false, "git", "commit", "-m",
                "[" + memberName + "] Update " + relativePath);
        if (commit.exitCode != 0) {
            return null;
        }
        CommandResult sha = runCommand(root, false, "git", "rev-parse", "HEAD");
        if (mode == WorkspaceMode.DISTRIBUTED && !push()) {
            pull();
            push();
        }
        return sha.exitCode == 0 ? sha.stdout.trim() : null;
    }

    public List<Map<String, String>> getHistory(String relativePath) {
        return getHistory(relativePath, 10);
    }

    public List<Map<String, String>> getHistory(String relativePath, int limit) {
        if (!config.isVersionControl()) {
            return List.of();
        }
        if (mode == WorkspaceMode.DISTRIBUTED) {
            pull();
        }
        CommandResult result = runCommand(Path.of(workspacePath), false, "git", "log",
                "--max-count=" + limit, "--format=%H|%an|%ai|%s", "--", relativePath);
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            return List.of();
        }
        List<Map<String, String>> history = new ArrayList<>();
        for (String line : result.stdout.split("\\R")) {
            String[] parts = line.split("\\|", 4);
            if (parts.length == 4) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("commit", parts[0]);
                item.put("author", parts[1]);
                item.put("date", parts[2]);
                item.put("message", parts[3]);
                history.add(item);
            }
        }
        return history;
    }

    public boolean acquireLock(String filePath, String memberName, String displayName) {
        return acquireLock(filePath, memberName, displayName, 300);
    }

    public boolean acquireLock(String filePath, String memberName, String displayName, int timeoutSeconds) {
        return acquireLock(new WorkspaceFileLock(
                filePath,
                memberName,
                displayName,
                Instant.now().toString(),
                timeoutSeconds
        ));
    }

    public boolean acquireLock(WorkspaceFileLock lock) {
        if (lock == null) {
            return false;
        }
        WorkspaceFileLock existing = locks.get(lock.getFilePath());
        if (existing != null && !existing.isExpired()) {
            if (!existing.getHolderId().equals(lock.getHolderId())) {
                return false;
            }
        }
        locks.put(lock.getFilePath(), lock);
        return true;
    }

    public boolean releaseLock(String filePath, String holderId) {
        WorkspaceFileLock existing = locks.get(filePath);
        if (existing == null || holderId == null || !holderId.equals(existing.getHolderId())) {
            return false;
        }
        locks.remove(filePath);
        return true;
    }

    public WorkspaceFileLock getLock(String filePath) {
        WorkspaceFileLock existing = locks.get(filePath);
        if (existing != null && existing.isExpired()) {
            locks.remove(filePath);
            return null;
        }
        return existing;
    }

    public List<WorkspaceFileLock> listLocks() {
        locks.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return List.copyOf(locks.values());
    }

    private static void createDirectoryLink(Path target, Path link) throws IOException {
        Files.createDirectories(target);
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException symlinkError) {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")
                    && createWindowsJunction(target, link)) {
                return;
            }
            Files.createDirectories(link);
        }
    }

    private static boolean createWindowsJunction(Path target, Path link) {
        CommandResult result = runCommand(Path.of("."), false, "cmd", "/c", "mklink", "/J",
                link.toString(), target.toString());
        return result.exitCode == 0;
    }

    private static CommandResult runCommand(Path cwd, boolean check, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(cwd.toFile());
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (check && exitCode != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command) + ": " + stderr);
            }
            return new CommandResult(exitCode, stdout, stderr);
        } catch (IOException error) {
            if (check) {
                throw new IllegalStateException("Failed to start command: " + String.join(" ", command), error);
            }
            return new CommandResult(127, "", error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (check) {
                throw new IllegalStateException("Command interrupted: " + String.join(" ", command), error);
            }
            return new CommandResult(130, "", "Interrupted");
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
