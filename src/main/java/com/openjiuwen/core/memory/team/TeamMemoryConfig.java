/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for team-scoped memory.
 * <p>
 * Mirrors Python's {@code TeamMemoryConfig} from
 * {@code core/memory/team/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemoryConfig {

    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private String scenario = "general";

    private Object embeddingConfig;

    @Builder.Default
    private boolean autoExtract = true;

    @Builder.Default
    private boolean sharedMemory = true;

    @Builder.Default
    private String memberMemoryPromptMode = "proactive";

    @Builder.Default
    private double timezoneOffsetHours = 8.0;

    /**
     * Temporary read-only memory source for the team.
     * Points to the workspace path of the parent agent that created the team.
     */
    private String parentWorkspacePath;

    /**
     * Absolute path to the team's shared memory directory.
     */
    private String teamMemoryDir;
}
