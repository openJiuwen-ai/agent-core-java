/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;

import java.util.List;

/**
 * Resolved session-scoped release info for one or more persisted teams.
 *
 * <p>Mirrors Python's {@code TeamSessionReleaseInfo} in
 * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
 */
public record TeamSessionReleaseInfo(List<String> teamNames, DatabaseConfig dbConfig) {
    public TeamSessionReleaseInfo {
        teamNames = teamNames == null ? List.of() : List.copyOf(teamNames);
    }
}
