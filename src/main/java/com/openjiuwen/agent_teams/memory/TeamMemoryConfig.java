/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.EmbeddingProviders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code TeamMemoryConfig} in
 * {@code openjiuwen/agent_teams/memory/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamMemoryConfig {

    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private String scenario = "general";

    @JsonIgnore
    private EmbeddingConfig embeddingConfig;

    @Builder.Default
    private boolean autoExtract = true;

    @Builder.Default
    private boolean sharedMemory = true;

    @Builder.Default
    private String memberMemoryPromptMode = "proactive";

    @Builder.Default
    private double timezoneOffsetHours = 8.0d;

    /**
     * Temporary read-only memory source for the team.
     * Points to the workspace path of the parent agent that created the team.
     */
    @JsonIgnore
    private String parentWorkspacePath;

    /**
     * Absolute path to the team's shared memory directory.
     * When null, callers derive it from the team home directory.
     */
    @JsonIgnore
    private String teamMemoryDir;

    public static EmbeddingConfig resolveEmbeddingConfig(TeamMemoryConfig config) {
        if (config != null && config.getEmbeddingConfig() != null) {
            return config.getEmbeddingConfig();
        }
        return EmbeddingProviders.resolveEmbeddingConfigFromEnv(null, null, null);
    }
}
