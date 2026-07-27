/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Retrieval strategy for episodes during add memory.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EpisodeRetrievalStrategy extends RetrievalStrategy {
    private boolean isSameKind = false;
    private boolean isExcludeFutureResults = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public EpisodeRetrievalStrategy() {
        setRankConfig(new RRFRankConfig());
        setMinScore(0.025);
    }
}
