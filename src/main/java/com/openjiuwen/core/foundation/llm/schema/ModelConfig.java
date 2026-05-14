/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
    /**
     * Creates a ModelConfig with the given model provider and default model info.
     *
     * @param modelProvider the model provider name
     */
    public ModelConfig(String modelProvider) {
        this(modelProvider, new BaseModelInfo());
    }

    public static ModelConfigBuilder builder() {
        return new ModelConfigBuilder();
    }

    public static final class ModelConfigBuilder {
        private String modelProvider;
        private BaseModelInfo modelInfo;

        public ModelConfigBuilder modelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public ModelConfigBuilder modelInfo(BaseModelInfo modelInfo) {
            this.modelInfo = modelInfo;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(modelProvider, modelInfo != null ? modelInfo : new BaseModelInfo());
        }
    }
}
