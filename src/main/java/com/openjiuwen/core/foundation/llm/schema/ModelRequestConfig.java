/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Model request configuration (per-request parameters).
 * <p>
 * Mirrors Python's {@code ModelRequestConfig} model.
 * Supports extra fields via {@link #extraFields}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelRequestConfig {

    @Builder.Default
    @JsonProperty("model")
    private String modelName = "";

    @Builder.Default
    private Double temperature = 0.95;

    @Builder.Default
    @JsonProperty("top_p")
    private Double topP = 0.1;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private String stop;

    private String user;

    private Integer seed;

    /** Extra fields that are not part of the standard config. */
    @Builder.Default
    private Map<String, Object> extraFields = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new HashMap<>();
        }
        extraFields.put(key, value);
    }

    public Double getTopP() {
        return topP;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public static ModelRequestConfigBuilder builder() {
        return new ModelRequestConfigBuilder();
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getUser() {
        return user;
    }

    public Integer getSeed() {
        return seed;
    }

    public String getStop() {
        return stop;
    }

    public static final class ModelRequestConfigBuilder {
        private String modelName = "";
        private Double temperature = 0.95;
        private Double topP = 0.1;
        private Integer maxTokens;
        private String stop;
        private String user;
        private Integer seed;
        private Map<String, Object> extraFields = new HashMap<>();

        public ModelRequestConfigBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public ModelRequestConfigBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ModelRequestConfigBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ModelRequestConfigBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public ModelRequestConfigBuilder stop(String stop) {
            this.stop = stop;
            return this;
        }

        public ModelRequestConfigBuilder user(String user) {
            this.user = user;
            return this;
        }

        public ModelRequestConfigBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public ModelRequestConfigBuilder extraFields(Map<String, Object> extraFields) {
            this.extraFields = extraFields;
            return this;
        }

        public ModelRequestConfig build() {
            ModelRequestConfig config = new ModelRequestConfig();
            config.modelName = this.modelName;
            config.temperature = this.temperature;
            config.topP = this.topP;
            config.maxTokens = this.maxTokens;
            config.stop = this.stop;
            config.user = this.user;
            config.seed = this.seed;
            config.extraFields = this.extraFields;
            return config;
        }
    }
}
