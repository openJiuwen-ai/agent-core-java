/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code RetrievalConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalConfig {

    @JsonProperty("top_k")
    @Builder.Default
    private int topK = 5;

    @JsonProperty("score_threshold")
    private Double scoreThreshold;

    @JsonProperty("use_graph")
    private Boolean useGraph;

    @Builder.Default
    private boolean agentic = false;

    @JsonProperty("graph_expansion")
    @Builder.Default
    private boolean graphExpansion = false;

    private Map<String, Object> filters;

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? null : new LinkedHashMap<>(filters);
    }
}
