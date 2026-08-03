/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import java.util.List;

/**
 * Package facade for agent-team memory exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.memory} in
 * {@code openjiuwen/agent_teams/memory/__init__.py}.</p>
 */
public final class MemoryPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/memory/__init__.py";
    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    public static final Class<TeamMemoryConfig> TEAM_MEMORY_CONFIG = TeamMemoryConfig.class;

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MemberMemoryToolkit",
            "TEAM_MEMORY_FILENAME",
            "TEAM_MEMORY_MAX_READ_LINES",
            "PromptMode",
            "TeamLanguage",
            "TeamLifecycle",
            "TeamMemoryConfig",
            "TeamMemoryManager",
            "TeamMemoryManagerParams",
            "TeamRole",
            "TeamScenario",
            "resolve_embedding_config"
    );

    private MemoryPackage() {
    }

    public static EmbeddingConfig resolveEmbeddingConfig(TeamMemoryConfig config) {
        return TeamMemoryConfig.resolveEmbeddingConfig(config);
    }
}
