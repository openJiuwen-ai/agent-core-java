/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.workspace;

/**
 * Conflict strategy for team workspace changes.
 *
 * <p>Mirrors Python's {@code ConflictStrategy} in
 * {@code openjiuwen.agent_teams.team_workspace.models}.</p>
 */
public enum ConflictStrategy {
    LOCK,
    MERGE,
    LAST_WRITE_WINS
}
