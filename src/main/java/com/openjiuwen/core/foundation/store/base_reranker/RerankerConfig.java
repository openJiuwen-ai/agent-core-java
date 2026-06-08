/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker model configuration.
 * <p>
 * Mirrors Python's {@code RerankerConfig} in
 * {@code openjiuwen/core/foundation/store/base_reranker.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankerConfig {

    @JsonProperty("api_key")
    @Builder.Default
    private String apiKey = "";

    @JsonProperty("api_base")
    private String apiBase;

    @JsonProperty("model_name")
    @JsonAlias("model")
    @Builder.Default
    private String modelName = "";

    @Builder.Default
    private double timeout = 10.0d;

    @JsonProperty("temperature")
    @Builder.Default
    private double temperature = 0.95d;

    @JsonProperty("top_p")
    @Builder.Default
    private double topP = 0.1d;

    @JsonProperty("yes_no_ids")
    private List<Integer> yesNoIds;

    @JsonProperty("extra_body")
    @Builder.Default
    private Map<String, Object> extraBody = new LinkedHashMap<>();
}
