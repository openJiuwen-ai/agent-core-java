/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;

/**
 * Scope-specific memory configuration.
 *
 * <p>Mirrors Python's {@code MemoryScopeConfig} in
 * {@code openjiuwen/core/memory/config/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryScopeConfig {

    public static final String DEFAULT_USER_PROFILE_DEFINITION =
            "用户本人的肯定或否定表述（包含不限于基本身份、兴趣偏好、人际关系、资产状况）";

    public static final String DEFAULT_SEMANTIC_MEMORY_DEFINITION =
            "用户对话中涉及的和时间无明确关系的事实性内容或概念";

    public static final String DEFAULT_EPISODIC_MEMORY_DEFINITION =
            "用户对话中涉及的和时间有明确关系的事实性内容或概念";

    @JsonProperty("model_cfg")
    private ModelRequestConfig modelCfg;

    @JsonProperty("model_client_cfg")
    private ModelClientConfig modelClientCfg;

    @JsonProperty("embedding_cfg")
    @JsonIgnoreProperties({"verify_ssl", "verifySsl", "ssl_cert", "sslCert"})
    private EmbeddingConfig embeddingCfg;

    @JsonProperty("user_profile_definition")
    private String userProfileDefinition = DEFAULT_USER_PROFILE_DEFINITION;

    @JsonProperty("semantic_memory_definition")
    private String semanticMemoryDefinition = DEFAULT_SEMANTIC_MEMORY_DEFINITION;

    @JsonProperty("episodic_memory_definition")
    private String episodicMemoryDefinition = DEFAULT_EPISODIC_MEMORY_DEFINITION;

    public MemoryScopeConfig() {
    }

    public MemoryScopeConfig(
            ModelRequestConfig modelCfg,
            ModelClientConfig modelClientCfg,
            EmbeddingConfig embeddingCfg,
            String userProfileDefinition,
            String semanticMemoryDefinition,
            String episodicMemoryDefinition) {
        this.modelCfg = modelCfg;
        this.modelClientCfg = modelClientCfg;
        this.embeddingCfg = embeddingCfg;
        setUserProfileDefinition(userProfileDefinition);
        setSemanticMemoryDefinition(semanticMemoryDefinition);
        setEpisodicMemoryDefinition(episodicMemoryDefinition);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModelRequestConfig getModelCfg() {
        return modelCfg;
    }

    public void setModelCfg(ModelRequestConfig modelCfg) {
        this.modelCfg = modelCfg;
    }

    public ModelClientConfig getModelClientCfg() {
        return modelClientCfg;
    }

    public void setModelClientCfg(ModelClientConfig modelClientCfg) {
        this.modelClientCfg = modelClientCfg;
    }

    public EmbeddingConfig getEmbeddingCfg() {
        return embeddingCfg;
    }

    public void setEmbeddingCfg(EmbeddingConfig embeddingCfg) {
        this.embeddingCfg = embeddingCfg;
    }

    public String getUserProfileDefinition() {
        return userProfileDefinition;
    }

    public void setUserProfileDefinition(String userProfileDefinition) {
        this.userProfileDefinition = userProfileDefinition == null
                ? DEFAULT_USER_PROFILE_DEFINITION
                : userProfileDefinition;
    }

    public String getSemanticMemoryDefinition() {
        return semanticMemoryDefinition;
    }

    public void setSemanticMemoryDefinition(String semanticMemoryDefinition) {
        this.semanticMemoryDefinition = semanticMemoryDefinition == null
                ? DEFAULT_SEMANTIC_MEMORY_DEFINITION
                : semanticMemoryDefinition;
    }

    public String getEpisodicMemoryDefinition() {
        return episodicMemoryDefinition;
    }

    public void setEpisodicMemoryDefinition(String episodicMemoryDefinition) {
        this.episodicMemoryDefinition = episodicMemoryDefinition == null
                ? DEFAULT_EPISODIC_MEMORY_DEFINITION
                : episodicMemoryDefinition;
    }

    public static final class Builder {
        private ModelRequestConfig modelCfg;
        private ModelClientConfig modelClientCfg;
        private EmbeddingConfig embeddingCfg;
        private String userProfileDefinition = DEFAULT_USER_PROFILE_DEFINITION;
        private String semanticMemoryDefinition = DEFAULT_SEMANTIC_MEMORY_DEFINITION;
        private String episodicMemoryDefinition = DEFAULT_EPISODIC_MEMORY_DEFINITION;

        private Builder() {
        }

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

        public Builder userProfileDefinition(String userProfileDefinition) {
            this.userProfileDefinition = userProfileDefinition;
            return this;
        }

        public Builder semanticMemoryDefinition(String semanticMemoryDefinition) {
            this.semanticMemoryDefinition = semanticMemoryDefinition;
            return this;
        }

        public Builder episodicMemoryDefinition(String episodicMemoryDefinition) {
            this.episodicMemoryDefinition = episodicMemoryDefinition;
            return this;
        }

        public MemoryScopeConfig build() {
            return new MemoryScopeConfig(
                    modelCfg,
                    modelClientCfg,
                    embeddingCfg,
                    userProfileDefinition,
                    semanticMemoryDefinition,
                    episodicMemoryDefinition
            );
        }
    }
}
