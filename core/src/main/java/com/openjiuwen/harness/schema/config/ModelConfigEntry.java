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
 * One model entry in a multi-model {@link DeepAgentConfig}.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigEntry {
    /**
     * Unique model identifier, for example {@code deepseek-v4}.
     */
    private String modelId;

    /**
     * Whether this entry is the default model. Exactly one entry should be default.
     */
    @Builder.Default
    private boolean isDefault = false;

    /**
     * Request-level configuration.
     */
    private ModelRequestConfig modelConfig;

    /**
     * Connection-level configuration.
     */
    private ModelClientConfig modelClient;
}
