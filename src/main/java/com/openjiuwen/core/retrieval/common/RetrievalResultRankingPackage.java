/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;

import java.util.Map;

/**
 * Package bridge for retrieval result-ranking exports.
 * <p>
 * Mirrors Python's module docstring and exports in
 * {@code openjiuwen/core/retrieval/common/result_ranking.py}.
 */
public final class RetrievalResultRankingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/retrieval/common/result_ranking.py";
    public static final String DESCRIPTION = "Result ranking for hybrid search";
    public static final Class<BaseRankConfig> BASE_RANK_CONFIG = BaseRankConfig.class;
    public static final Class<WeightedRankConfig> WEIGHTED_RANK_CONFIG = WeightedRankConfig.class;
    public static final Class<RRFRankConfig> RRF_RANK_CONFIG = RRFRankConfig.class;

    private RetrievalResultRankingPackage() {
    }

    public static void registerResultRankerCls(String database, Object weighted, Object rrf) {
        RankConfigRegistry.registerResultRankerCls(database, weighted, rrf);
    }

    public static void registerResultRankerCls(String database, Object weighted, Object rrf, Map<String, Object> extra) {
        RankConfigRegistry.registerResultRankerCls(database, weighted, rrf, extra);
    }
}
