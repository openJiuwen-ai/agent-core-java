/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) rank configuration.
 * <p>
 * Mirrors Python's {@code RRFRankConfig} model from
 * <code>foundation/store/graph/result_ranking.py</code>.
 */
public class RRFRankConfig extends BaseRankConfig {

    private int k = 60;

    public RRFRankConfig() {
        setName("rrf");
    }

    public RRFRankConfig(int k) {
        setName("rrf");
        this.k = k;
    }

    @Override
    public RankerArgs getArgs() {
        List<Object> positional = new ArrayList<>();
        Map<String, Object> keyword = new HashMap<>();
        keyword.put("k", k);
        return new RankerArgs(positional, keyword);
    }

    public int getK() { return k; }
    public void setK(int k) { this.k = k; }
}
