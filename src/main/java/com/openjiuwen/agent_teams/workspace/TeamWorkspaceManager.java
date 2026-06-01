/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

    public void mountIntoWorkspace(String workspaceRoot) {
        try {
            Path teamDir = Path.of(workspaceRoot, ".team");
            Files.createDirectories(teamDir);
            Path link = teamDir.resolve(teamName);
            if (!Files.exists(link)) {
                try {
                    Files.createSymbolicLink(link, Path.of(workspacePath));
                } catch (IOException symlinkError) {
                    Files.createDirectories(link);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to mount team workspace", e);
        }
    }

    public boolean acquireLock(WorkspaceFileLock lock) {
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
}
