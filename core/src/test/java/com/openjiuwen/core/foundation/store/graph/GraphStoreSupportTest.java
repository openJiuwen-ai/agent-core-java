/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphStoreSupportTest {

    @Test
    void graphStoreConstantsMatchPythonModule() {
        assertEquals("ENTITY_COLLECTION", GraphStoreConstants.ENTITY_COLLECTION);
        assertEquals("RELATION_COLLECTION", GraphStoreConstants.RELATION_COLLECTION);
        assertEquals("EPISODE_COLLECTION", GraphStoreConstants.EPISODE_COLLECTION);
        assertEquals(Map.of("gt", 1, "le", 65535), GraphStoreConstants.VARCHAR_LIMIT);
        assertEquals(Map.of("gt", 1, "le", 4096), GraphStoreConstants.ARRAY_LIMIT);
        assertEquals(10, GraphStoreConstants.DEFAULT_WORKER_NUM);
    }

    @Test
    void rankConfigRegistryStoresAndReturnsEntries() {
        Object weighted = new Object();
        Object rrf = new Object();
        RankConfigRegistry.registerResultRankerCls("milvus", weighted, rrf, Map.of("extra", "value"));

        assertEquals(weighted, RankConfigRegistry.getRankerCls("milvus", "weighted"));
        assertEquals(rrf, RankConfigRegistry.getRankerCls("milvus", "rrf"));
        assertEquals("value", RankConfigRegistry.getRankerCls("milvus", "extra"));
        assertTrue(RankConfigRegistry.hasRanker("milvus"));
        assertTrue(RankConfigRegistry.hasRanker("milvus", "weighted"));
        assertNull(RankConfigRegistry.getRankerCls("missing", "weighted"));
    }

    @Test
    void baseRankConfigDefaultsMatchPython() {
        BaseRankConfig config = new BaseRankConfig() {
            @Override
            public RankerArgs getArgs() {
                return new RankerArgs(List.of(), Map.of());
            }
        };

        assertEquals("base", config.getName());
        assertFalse(config.isHigherIsBetter());
        assertEquals(List.of(1, 1, 1), config.getIsActive());
        assertNull(config.getRankerCls("missing"));
    }

    @Test
    void weightedRankConfigNormalizesPositiveWeights() {
        WeightedRankConfig config = new WeightedRankConfig(0.2, 0.2, 0.2);
        BaseRankConfig.RankerArgs args = config.getArgs();

        double total = args.getPositional().stream()
                .mapToDouble(value -> (Double) value)
                .sum();
        assertEquals(1.0d, total, 1.0e-9);
        assertEquals(List.of(1, 1, 1), config.getIsActive());

        WeightedRankConfig zero = new WeightedRankConfig(0.0d, 0.0d, 0.0d);
        assertTrue(zero.getArgs().getPositional().isEmpty());
        assertEquals(List.of(0, 0, 0), zero.getIsActive());
    }

    @Test
    void rrfRankConfigExposesPythonDefaults() {
        RRFRankConfig config = new RRFRankConfig();
        assertEquals("rrf", config.getName());
        assertTrue(config.isHigherIsBetter());
        assertEquals(40, config.getK());
        assertEquals(List.of(40), config.getArgs().getPositional());
        assertEquals(List.of(1, 1, 1), config.getIsActive());

        RRFRankConfig sparseOnly = new RRFRankConfig(false, false, true);
        assertEquals(List.of(0, 0, 1), sparseOnly.getIsActive());
    }
}
