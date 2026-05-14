/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal team workspace configuration.
 *
 * <p>Mirrors Python's {@code TeamWorkspaceConfig} in
 * {@code openjiuwen.agent_teams.team_workspace.models}.</p>
 */
public class TeamWorkspaceConfig {

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public List<String> getArtifactDirs() {
        return new ArrayList<>(artifactDirs);
    }

    public void setArtifactDirs(List<String> artifactDirs) {
        this.artifactDirs = artifactDirs != null ? new ArrayList<>(artifactDirs) : new ArrayList<>();
    }

    public boolean isVersionControl() {
        return versionControl;
    }

    public void setVersionControl(boolean versionControl) {
        this.versionControl = versionControl;
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }
}
