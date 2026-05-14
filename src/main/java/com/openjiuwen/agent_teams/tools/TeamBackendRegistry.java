/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for shared Java team backend stores.
 *
 * <p>Mirrors the move toward reusable backend/database ownership in Python's
 * team tools layer.</p>
 */
public final class TeamBackendRegistry {

    private static final Map<String, TeamBackendStore> STORES = new LinkedHashMap<>();

    private TeamBackendRegistry() {
    }

    public static synchronized TeamBackendStore getOrCreate(String teamName) {
        return STORES.computeIfAbsent(teamName != null ? teamName : "default", TeamBackendStore::new);
    }

    public static synchronized void clear() {
        STORES.clear();
    }
}
