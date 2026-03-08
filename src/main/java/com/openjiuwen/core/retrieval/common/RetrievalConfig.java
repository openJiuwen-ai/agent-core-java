/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retrieval-time options.
 */
public class RetrievalConfig {

    private int topK = 5;
    private Double scoreThreshold;
    private Boolean useGraph;
    private boolean agentic = false;
    private boolean graphExpansion = false;
    private Map<String, Object> filters;

    public RetrievalConfig() {
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        RetrievalValidation.requirePositive(topK, "RetrievalConfig.topK", com.openjiuwen.core.common.exception.StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND);
        this.topK = topK;
    }

    public Double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(Double scoreThreshold) {
        if (scoreThreshold != null && (scoreThreshold.isNaN() || scoreThreshold.isInfinite())) {
            throw RetrievalExceptions.validation("RetrievalConfig.scoreThreshold must be finite");
        }
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
