/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-request model generation configuration.
 *
 * <p>Mirrors Python's {@code ModelRequestConfig} in
 * {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
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
    private double temperature = 0.95;

    @Builder.Default
    @JsonProperty("top_p")
    private double topP = 0.1;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private String stop;

    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new LinkedHashMap<>();
        }
        extraFields.put(key, value);
    }
}
