/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Team memory configuration.
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
    private EmbeddingConfig embeddingConfig;
    @Builder.Default
    private boolean isAutoExtract = true;
    @Builder.Default
    private boolean sharedMemory = true;
    @Builder.Default
    private String memberMemoryPromptMode = "isProactive";
    @Builder.Default
    private double timezoneOffsetHours = 8.0;
    private String parentWorkspacePath;
    private String teamMemoryDir;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static EmbeddingConfig resolveEmbeddingConfig(TeamMemoryConfig config) {
        if (config != null && config.getEmbeddingConfig() != null) {
            return config.getEmbeddingConfig();
        }
        String modelName = System.getenv("EMBEDDING_MODEL_NAME");
        String baseUrl = System.getenv("EMBEDDING_BASE_URL");
        String apiKey = System.getenv("EMBEDDING_API_KEY");
        if (modelName != null && !modelName.isBlank()
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()) {
            return new EmbeddingConfig(modelName, baseUrl, apiKey);
        }
        return null;
    }
}
