/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for result ranking config and registry behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.store.graph.test_result_ranking} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_result_ranking.py}.</p>
 */
class ResultRankingPythonParityTest {

    @Test
    void registerAndRetrieveFromRankerCls() {
        Object weighted = new Object();
        Object rrf = new Object();

        RankConfigRegistry.registerResultRankerCls("test_db_mtt00265", weighted, rrf, Map.of("extra", "value"));

        assertSame(weighted, RankConfigRegistry.getRankerCls("test_db_mtt00265", "weighted"));
        assertSame(rrf, RankConfigRegistry.getRankerCls("test_db_mtt00265", "rrf"));
        assertEquals("value", RankConfigRegistry.getRankerCls("test_db_mtt00265", "extra"));
    }

    @Test
    void abstractCannotInstantiate() {
        assertTrue(Modifier.isAbstract(BaseRankConfig.class.getModifiers()));
    }

    @Test
    void nameBaseHigherIsBetterFalse() {
        ConcreteRankConfig config = new ConcreteRankConfig();

        assertEquals("base", config.getName());
        assertFalse(config.isHigherIsBetter());
    }

    @Test
    void isActiveReturnsOnes() {
        ConcreteRankConfig config = new ConcreteRankConfig();

        assertEquals(List.of(1, 1, 1), config.getIsActive());
    }

    @Test
    void getRankerClsReturnsFromRankerCls() {
        ConcreteRankConfig config = new ConcreteRankConfig();
        config.setName("weighted");

        assertNull(config.getRankerCls("nonexistent_db_xyz"));
    }

    @Test
    void weightedRankConfigDefaults() {
        WeightedRankConfig config = new WeightedRankConfig();

        assertEquals(0.15d, config.getNameDense(), 1.0e-12);
        assertEquals(0.6d, config.getContentDense(), 1.0e-12);
        assertEquals(0.25d, config.getContentSparse(), 1.0e-12);
    }

    @Test
    void weightedRankConfigArgsNormalizeWeightsToOne() {
        WeightedRankConfig config = new WeightedRankConfig(0.2d, 0.2d, 0.2d);

        BaseRankConfig.RankerArgs args = config.getArgs();
        double total = args.getPositional().stream()
                .mapToDouble(value -> (Double) value)
                .sum();

        assertEquals(1.0d, total, 1.0e-12);
        assertEquals(Map.of(), args.getKeyword());
    }

    @Test
    void weightedRankConfigZeroWeightsReturnEmptyArgs() {
        WeightedRankConfig config = new WeightedRankConfig(0.0d, 0.0d, 0.0d);

        BaseRankConfig.RankerArgs args = config.getArgs();

        assertEquals(List.of(), args.getPositional());
        assertEquals(Map.of(), args.getKeyword());
    }

    @Test
    void weightedRankConfigBoundsWeightsInZeroOne() {
        WeightedRankConfig config = new WeightedRankConfig(0.0d, 1.0d, 0.5d);

        assertTrue(config.getNameDense() >= 0.0d && config.getNameDense() <= 1.0d);
        assertTrue(config.getContentDense() >= 0.0d && config.getContentDense() <= 1.0d);
        assertTrue(config.getContentSparse() >= 0.0d && config.getContentSparse() <= 1.0d);
        assertThrows(IllegalArgumentException.class, () -> new WeightedRankConfig(1.5d, 0.0d, 0.0d));
    }

    @Test
    void rrfRankConfigDefaults() {
        RRFRankConfig config = new RRFRankConfig();

        assertEquals("rrf", config.getName());
        assertTrue(config.isHigherIsBetter());
        assertEquals(40, config.getK());
        assertTrue(config.isNameDense());
        assertTrue(config.isContentDense());
        assertTrue(config.isContentSparse());
    }

    @Test
    void rrfRankConfigArgsReturnsKAndEmptyKeywordArgs() {
        RRFRankConfig config = new RRFRankConfig(60);

        BaseRankConfig.RankerArgs args = config.getArgs();

        assertEquals(List.of(60), args.getPositional());
        assertEquals(Map.of(), args.getKeyword());
    }

    @Test
    void rrfRankConfigIsActiveFromBools() {
        RRFRankConfig first = new RRFRankConfig(true, false, true);
        RRFRankConfig second = new RRFRankConfig(false, false, false);

        assertEquals(List.of(1, 0, 1), first.getIsActive());
        assertEquals(List.of(0, 0, 0), second.getIsActive());
    }

    private static final class ConcreteRankConfig extends BaseRankConfig {

        @Override
        public RankerArgs getArgs() {
            return new RankerArgs(List.of(), Map.of());
        }
    }
}
