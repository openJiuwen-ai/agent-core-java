/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
}
