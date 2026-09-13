/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.harness.workspace.Workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Construction parameters for TeamMemoryManager.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemoryManagerParams {
    private String memberName;
    private String teamName;
    private TeamRole role;
    private TeamLifecycle lifecycle;
    private TeamScenario scenario;
    private EmbeddingConfig embeddingConfig;
    private Workspace workspace;
    private Object sysOperation;
    private String teamMemoryDir;
    @Builder.Default
    private TeamLanguage language = TeamLanguage.CN;
    @Builder.Default
    private PromptMode promptMode = PromptMode.PROACTIVE;
    @Builder.Default
    private boolean isAutoExtractEnabled = true;
    private String readOnlySourceWorkspace;
    private TeamDatabase db;
    private com.openjiuwen.agentteams.tools.TeamTaskManager taskManager;
    private Model extractionModel;
    @Builder.Default
    private double timezoneOffsetHours = 8.0;

    /**
     * TeamMemoryManagerParamsBuilder.
     * 
     * @since 0.1.7
     */
    public static class TeamMemoryManagerParamsBuilder {
        /**
         * enableAutoExtract.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public TeamMemoryManagerParamsBuilder enableAutoExtract(boolean value) {
            return this.isAutoExtractEnabled(value);
        }
    }
}
