/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

/**
 * Memory scope configuration.
 * Corresponds to Python: config/config.py - MemoryScopeConfig
 */
public class MemoryScopeConfig {

    private final ModelRequestConfig modelCfg;
    private final ModelClientConfig modelClientCfg;
    private final EmbeddingConfig embeddingCfg;

    private MemoryScopeConfig(Builder builder) {
        this.modelCfg = builder.modelCfg;
        this.modelClientCfg = builder.modelClientCfg;
        this.embeddingCfg = builder.embeddingCfg;
    }

    public ModelRequestConfig getModelCfg() {
        return modelCfg;
    }

    public ModelClientConfig getModelClientCfg() {
        return modelClientCfg;
    }

    public EmbeddingConfig getEmbeddingCfg() {
        return embeddingCfg;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ModelRequestConfig modelCfg;
        private ModelClientConfig modelClientCfg;
        private EmbeddingConfig embeddingCfg;

        public Builder modelCfg(ModelRequestConfig modelCfg) {
            this.modelCfg = modelCfg;
            return this;
        }

        public Builder modelClientCfg(ModelClientConfig modelClientCfg) {
            this.modelClientCfg = modelClientCfg;
            return this;
        }

        public Builder embeddingCfg(EmbeddingConfig embeddingCfg) {
            this.embeddingCfg = embeddingCfg;
            return this;
        }

        public MemoryScopeConfig build() {
            return new MemoryScopeConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryScopeConfig{" +
               "modelCfg=" + modelCfg +
               ", modelClientCfg=" + modelClientCfg +
               ", embeddingCfg=" + embeddingCfg +
               '}';
    }
}

