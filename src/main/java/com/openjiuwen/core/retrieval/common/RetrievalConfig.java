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

/**
 * Mirrors Python's {@code RetrievalConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
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

    public RetrievalConfig() {
        this.topK = 5;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        RetrievalValidation.requirePositive(
                topK,
                "RetrievalConfig.topK",
                com.openjiuwen.core.common.exception.StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND
        );
        this.topK = topK;
    }

    public Double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(Double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public Boolean getUseGraph() {
        return useGraph;
    }

    public void setUseGraph(Boolean useGraph) {
        this.useGraph = useGraph;
    }

    public boolean isAgentic() {
        return agentic;
    }

    public void setAgentic(boolean agentic) {
        this.agentic = agentic;
    }

    public boolean isGraphExpansion() {
        return graphExpansion;
    }

    public void setGraphExpansion(boolean graphExpansion) {
        this.graphExpansion = graphExpansion;
    }

    public Map<String, Object> getFilters() {
        return filters == null ? null : new LinkedHashMap<>(filters);
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? null : new LinkedHashMap<>(filters);
    }
}
