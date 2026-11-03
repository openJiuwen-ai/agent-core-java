/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.List;
import java.util.Map;

/**
 * RRF ranker configuration.
 * 
 * @since 0.1.7
 */
public class RRFRankConfig extends BaseRankConfig {
    private int k = 40;
    private boolean denseName = true;
    private boolean denseContent = true;
    private boolean sparseContent = true;

    /**
     * RRFRankConfig.
     * 
     * @since 0.1.7
     */
    public RRFRankConfig() {
        super("rrf", true);
    }

    /**
     * getArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public RankerArguments getArgs() {
        return new RankerArguments(List.of(k), Map.of());
    }

    /**
     * isActive.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Integer> isActive() {
        return List.of(denseName ? 1 : 0, denseContent ? 1 : 0, sparseContent ? 1 : 0);
    }

    /**
     * getK.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getK() {
        return k;
    }

    /**
     * setK.
     * 
     * @param k k
     * @since 0.1.7
     */
    public void setK(int k) {
        if (k < 0) {
            throw RetrievalExceptions.validation("RRFRankConfig.k must be >= 0");
        }
        this.k = k;
    }

    /**
     * isDenseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isDenseName() {
        return denseName;
    }

    /**
     * setDenseName.
     * 
     * @param denseName denseName
     * @since 0.1.7
     */
    public void setDenseName(boolean denseName) {
        this.denseName = denseName;
    }

    /**
     * isDenseContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isDenseContent() {
        return denseContent;
    }

    /**
     * setDenseContent.
     * 
     * @param denseContent denseContent
     * @since 0.1.7
     */
    public void setDenseContent(boolean denseContent) {
        this.denseContent = denseContent;
    }

    /**
     * isSparseContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSparseContent() {
        return sparseContent;
    }

    /**
     * setSparseContent.
     * 
     * @param sparseContent sparseContent
     * @since 0.1.7
     */
    public void setSparseContent(boolean sparseContent) {
        this.sparseContent = sparseContent;
    }
}
