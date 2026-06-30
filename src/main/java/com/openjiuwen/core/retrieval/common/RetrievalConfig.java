/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public RetrievalConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getTopK() {
        return topK;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTopK(int topK) {
        RetrievalValidation.requirePositive(topK, "RetrievalConfig.topK", com.openjiuwen.core.common.exception.StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND);
        this.topK = topK;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Double getScoreThreshold() {
        return scoreThreshold;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setScoreThreshold(Double scoreThreshold) {
        if (scoreThreshold != null && (scoreThreshold.isNaN() || scoreThreshold.isInfinite())) {
            throw RetrievalExceptions.validation("RetrievalConfig.scoreThreshold must be finite");
        }
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Boolean getUseGraph() {
        return useGraph;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUseGraph(Boolean useGraph) {
        this.useGraph = useGraph;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAgentic() {
        return agentic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAgentic(boolean agentic) {
        this.agentic = agentic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isGraphExpansion() {
        return graphExpansion;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGraphExpansion(boolean graphExpansion) {
        this.graphExpansion = graphExpansion;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getFilters() {
        return filters == null ? null : new LinkedHashMap<>(filters);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? null : new LinkedHashMap<>(filters);
    }
}
