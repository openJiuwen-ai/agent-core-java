/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * Model configuration combining provider info and model info.
 * <p>
 * Mirrors Python's {@code ModelConfig} dataclass.
 * 
 * @since 0.1.7
 */
public record ModelConfig(String modelProvider, BaseModelInfo modelInfo) {
    public ModelConfig(String modelProvider) {
        this(modelProvider, new BaseModelInfo());
    }
}
