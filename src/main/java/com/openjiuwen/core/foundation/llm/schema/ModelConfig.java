/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

/**
 * Model configuration combining provider info and model info.
 * <p>
 * Mirrors Python's {@code ModelConfig} dataclass.
 *
 * @param modelProvider the model provider name (e.g., "OpenAI", "DashScope")
 * @param modelInfo     the detailed model connection info
 */
public record ModelConfig(
        String modelProvider,
        BaseModelInfo modelInfo
) {
    public ModelConfig(String modelProvider) {
        this(modelProvider, new BaseModelInfo());
    }
}
