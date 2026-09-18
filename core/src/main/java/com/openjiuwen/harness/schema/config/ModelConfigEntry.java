/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single model configuration entry within a multi-model {@link DeepAgentConfig}.
 * <p>
 * Each entry defines a model identifier, whether it is the default model, and the
 * request-level and connection-level configuration for that model.
 *
 * @since 0.1.16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigEntry {
    /**
     * Unique model identifier, e.g. "deepseek-v4", "glm-5.1".
     */
    private String modelId;

    /**
     * Whether this entry is the default model. Exactly one entry should be default.
     */
    @Builder.Default
    private boolean isDefault = false;

    /**
     * Request-level configuration (modelName, temperature, topP, maxTokens, etc.).
     */
    private ModelRequestConfig modelConfig;

    /**
     * Connection-level configuration (provider, apiKey, apiBase, etc.).
     */
    private ModelClientConfig modelClient;
}
