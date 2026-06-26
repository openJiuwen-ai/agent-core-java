/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;

/**
 * Retrieval strategy during add-memory operations.
 * <p>
 * Mirrors Python's {@code RetrievalStrategy} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public class RetrievalStrategy extends BaseStrategy {

    @JsonProperty("same_kind")
    private boolean sameKind;

    public RetrievalStrategy() {
        this(3, 0.3d, new com.openjiuwen.core.foundation.store.graph.RRFRankConfig(), false);
    }

    public RetrievalStrategy(int topK, double minScore, BaseRankConfig rankConfig, boolean sameKind) {
        super(topK, minScore, rankConfig);
        this.sameKind = sameKind;
    }

    public boolean isSameKind() {
        return sameKind;
    }

    public void setSameKind(boolean sameKind) {
        this.sameKind = sameKind;
    }
}
