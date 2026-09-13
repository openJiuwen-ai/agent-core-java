/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retrieval-time options.
 * 
 * @since 0.1.7
 */
public class RetrievalConfig {
    private int topK = 5;
    private Double scoreThreshold;
    private Boolean useGraph;
    private boolean agentic = false;
    private boolean graphExpansion = false;
    private Map<String, Object> filters;

    /**
     * RetrievalConfig.
     * 
     * @since 0.1.7
     */
    public RetrievalConfig() {
    }

    /**
     * getTopK.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getTopK() {
        return topK;
    }

    /**
     * setTopK.
     * 
     * @param topK topK
     * @since 0.1.7
     */
    public void setTopK(int topK) {
        RetrievalValidation.requirePositive(topK, "RetrievalConfig.topK",
                com.openjiuwen.core.common.exception.StatusCode.RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND);
        this.topK = topK;
    }

    /**
     * getScoreThreshold.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getScoreThreshold() {
        return scoreThreshold;
    }

    /**
     * setScoreThreshold.
     * 
     * @param scoreThreshold scoreThreshold
     * @since 0.1.7
     */
    public void setScoreThreshold(Double scoreThreshold) {
        if (scoreThreshold != null && (scoreThreshold.isNaN() || scoreThreshold.isInfinite())) {
            throw RetrievalExceptions.validation("RetrievalConfig.scoreThreshold must be finite");
        }
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * getUseGraph.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Boolean getUseGraph() {
        return useGraph;
    }

    /**
     * setUseGraph.
     * 
     * @param useGraph useGraph
     * @since 0.1.7
     */
    public void setUseGraph(Boolean useGraph) {
        this.useGraph = useGraph;
    }

    /**
     * isAgentic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAgentic() {
        return agentic;
    }

    /**
     * setAgentic.
     * 
     * @param agentic agentic
     * @since 0.1.7
     */
    public void setAgentic(boolean agentic) {
        this.agentic = agentic;
    }

    /**
     * isGraphExpansion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isGraphExpansion() {
        return graphExpansion;
    }

    /**
     * setGraphExpansion.
     * 
     * @param graphExpansion graphExpansion
     * @since 0.1.7
     */
    public void setGraphExpansion(boolean graphExpansion) {
        this.graphExpansion = graphExpansion;
    }

    /**
     * getFilters.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getFilters() {
        return filters == null ? null : new LinkedHashMap<>(filters);
    }

    /**
     * setFilters.
     * 
     * @param filters filters
     * @since 0.1.7
     */
    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? null : new LinkedHashMap<>(filters);
    }
}
