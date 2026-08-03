/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;

/**
 * Retrieval strategy for episodes during add-memory operations.
 * <p>
 * Mirrors Python's {@code EpisodeRetrievalStrategy} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public class EpisodeRetrievalStrategy extends RetrievalStrategy {

    @JsonProperty("exclude_future_results")
    private boolean excludeFutureResults;

    public EpisodeRetrievalStrategy() {
        super(3, 0.025d, new RRFRankConfig(), false);
        this.excludeFutureResults = true;
    }

    public boolean isExcludeFutureResults() {
        return excludeFutureResults;
    }

    public void setExcludeFutureResults(boolean excludeFutureResults) {
        this.excludeFutureResults = excludeFutureResults;
    }
}
