/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.core.retrieval.common.BaseRankConfig;
import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import lombok.Data;

/**
 * Retrieval strategy during add memory.
 */
@Data
public class BaseStrategy {
    private int topK = 3;
    private double minScore = 0.3;
    private BaseRankConfig rankConfig = new RRFRankConfig();
}
