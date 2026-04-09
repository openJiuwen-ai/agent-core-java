  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.retrieval.common;

import java.util.List;
import java.util.Map;

/**
 * RRF ranker configuration.
 */
public class RRFRankConfig extends BaseRankConfig {

    private int k = 40;
    private boolean denseName = true;
    private boolean denseContent = true;
    private boolean sparseContent = true;

    public RRFRankConfig() {
        super("rrf", true);
    }

    @Override
    public RankerArguments getArgs() {
        return new RankerArguments(List.of(k), Map.of());
    }

    @Override
    public List<Integer> isActive() {
        return List.of(denseName ? 1 : 0, denseContent ? 1 : 0, sparseContent ? 1 : 0);
    }

    public int getK() {
        return k;
    }

    public void setK(int k) {
        if (k < 0) {
            throw RetrievalExceptions.validation("RRFRankConfig.k must be >= 0");
        }
        this.k = k;
    }

    public boolean isDenseName() {
        return denseName;
    }

    public void setDenseName(boolean denseName) {
        this.denseName = denseName;
    }

    public boolean isDenseContent() {
        return denseContent;
    }

    public void setDenseContent(boolean denseContent) {
        this.denseContent = denseContent;
    }

    public boolean isSparseContent() {
        return sparseContent;
    }

    public void setSparseContent(boolean sparseContent) {
        this.sparseContent = sparseContent;
    }
}
