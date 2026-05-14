/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

import java.time.Instant;

/**
 * Minimal file-level lock entry for team shared workspace.
 *
 * <p>Mirrors Python's {@code WorkspaceFileLock} in
 * {@code openjiuwen.agent_teams.team_workspace.models}.</p>
 */
public class WorkspaceFileLock {

    private final String filePath;
    private final String holderId;
    private final String holderName;
    private final String acquiredAt;
    private final int timeoutSeconds;

    public WorkspaceFileLock(String filePath, String holderId, String holderName, String acquiredAt) {
        this(filePath, holderId, holderName, acquiredAt, 300);
    }

    public WorkspaceFileLock(String filePath, String holderId, String holderName, String acquiredAt, int timeoutSeconds) {
        this.filePath = filePath;
        this.holderId = holderId;
        this.holderName = holderName;
        this.acquiredAt = acquiredAt;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getHolderId() {
        return holderId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getAcquiredAt() {
        return acquiredAt;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isExpired() {
        Instant acquired = Instant.parse(acquiredAt);
        return acquired.plusSeconds(timeoutSeconds).isBefore(Instant.now());
    }
}
