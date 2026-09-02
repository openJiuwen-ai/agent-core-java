/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.core.retrieval.common.BaseRankConfig;
import com.openjiuwen.core.retrieval.common.RRFRankConfig;

import lombok.Data;

/**
 * Retrieval strategy during add memory.
 * 
 * @since 0.1.7
 */
@Data
public class BaseStrategy {
    private int topK = 3;
    private double minScore = 0.3;

    /**
     * RRFRankConfig.
     * 
     * @since 0.1.7
     */
    private BaseRankConfig rankConfig = new RRFRankConfig();
}
