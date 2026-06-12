/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;

import java.util.Objects;

/**
 * Retrieval strategy during graph-memory operations.
 * <p>
 * Mirrors Python's {@code BaseStrategy} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public class BaseStrategy {

    @JsonProperty("top_k")
    private int topK;
    @JsonProperty("min_score")
    private double minScore;
    @JsonProperty("rank_config")
    private BaseRankConfig rankConfig;

    public BaseStrategy() {
        this(3, 0.3d, new RRFRankConfig());
    }

    public BaseStrategy(int topK, double minScore, BaseRankConfig rankConfig) {
        setTopK(topK);
        this.minScore = minScore;
        setRankConfig(rankConfig);
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("top_k must be >= 1");
        }
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public BaseRankConfig getRankConfig() {
        return rankConfig;
    }

    public void setRankConfig(BaseRankConfig rankConfig) {
        this.rankConfig = Objects.requireNonNull(rankConfig, "rankConfig");
    }
}
