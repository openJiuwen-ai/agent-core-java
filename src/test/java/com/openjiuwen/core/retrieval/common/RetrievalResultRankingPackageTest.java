/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.RankConfigRegistry;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's export checks in
 * {@code openjiuwen/core/retrieval/common/result_ranking.py}.
 */
class RetrievalResultRankingPackageTest {

    @Test
    void exportsMatchPythonModuleSurface() {
        assertEquals(
                "openjiuwen/core/retrieval/common/result_ranking.py",
                RetrievalResultRankingPackage.PYTHON_MODULE);
        assertEquals("Result ranking for hybrid search", RetrievalResultRankingPackage.DESCRIPTION);
        assertEquals(BaseRankConfig.class, RetrievalResultRankingPackage.BASE_RANK_CONFIG);
        assertEquals(WeightedRankConfig.class, RetrievalResultRankingPackage.WEIGHTED_RANK_CONFIG);
        assertEquals(RRFRankConfig.class, RetrievalResultRankingPackage.RRF_RANK_CONFIG);
    }

    @Test
    void registerResultRankerClsDelegatesToGraphRegistry() {
        String database = "retrieval-common-test";
        Object weighted = new Object();
        Object rrf = new Object();

        RetrievalResultRankingPackage.registerResultRankerCls(database, weighted, rrf, Map.of("extra", "value"));

        assertEquals(weighted, RankConfigRegistry.getRankerCls(database, "weighted"));
        assertEquals(rrf, RankConfigRegistry.getRankerCls(database, "rrf"));
        assertEquals("value", RankConfigRegistry.getRankerCls(database, "extra"));
        assertTrue(RankConfigRegistry.hasRanker(database));
    }
}
