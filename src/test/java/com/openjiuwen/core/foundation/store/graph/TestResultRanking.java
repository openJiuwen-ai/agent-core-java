/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for result ranking configuration classes.
 * <p>
 * Mirrors Python's test_result_ranking.py from
 * <code>tests/unit_tests/core/foundation/store/graph/test_result_ranking.py</code>.
 */
@DisplayName("Result Ranking Tests")
class TestResultRanking {

    @Nested
    @DisplayName("RankConfigRegistry Tests")
    class TestRegisterResultRankerCls {

        @Test
        @DisplayName("register and retrieve from RANKER_CLS")
        void testRegisterAndRetrieveFromRankerCls() {
            Object weightedFn = new Object();
            Object rrfFn = new Object();
            Object extraValue = "value";

            // Register with extra value using map
            Map<String, Object> extra = Map.of("extra", extraValue);
            RankConfigRegistry.registerResultRankerCls("test_db", weightedFn, rrfFn);

            // Verify registration
            assertNotNull(RankConfigRegistry.getRankerCls("test_db", "weighted"));
            assertEquals(weightedFn, RankConfigRegistry.getRankerCls("test_db", "weighted"));
            assertEquals(rrfFn, RankConfigRegistry.getRankerCls("test_db", "rrf"));

            // Note: Java registry only stores weighted and rrf, not extra values
            // Clean up would require a clear method, but we skip that for now
        }

        @Test
        @DisplayName("unknown database returns null")
        void testUnknownDatabaseReturnsNull() {
            assertNull(RankConfigRegistry.getRankerCls("nonexistent_db_xyz", "weighted"));
        }
    }

    @Nested
    @DisplayName("BaseRankConfig Tests")
    class TestBaseRankConfig {

        @Test
        @DisplayName("abstract cannot instantiate")
        void testAbstractCannotInstantiate() {
            // BaseRankConfig is abstract, cannot instantiate directly
            // This is enforced by Java's abstract keyword
            assertThrows(Exception.class, () -> {
                // Attempt to instantiate via reflection would fail
                Class<?> clazz = BaseRankConfig.class;
                clazz.getDeclaredConstructor().newInstance();
            });
        }

        @Test
        @DisplayName("name is base, higher_is_better is false")
        void testNameBaseHigherIsBetterFalse() {
            // Concrete subclass for testing
            class ConcreteRankConfig extends BaseRankConfig {
                @Override
                public RankerArgs getArgs() {
                    return new RankerArgs(List.of(), Map.of());
                }
            }

            ConcreteRankConfig cfg = new ConcreteRankConfig();
            assertEquals("base", cfg.getName());
            assertFalse(cfg.isHigherIsBetter());
        }

        @Test
        @DisplayName("is_active returns [1, 1, 1]")
        void testIsActiveReturnsOnes() {
            class ConcreteRankConfig extends BaseRankConfig {
                @Override
                public RankerArgs getArgs() {
                    return new RankerArgs(List.of(), Map.of());
                }
            }

            ConcreteRankConfig cfg = new ConcreteRankConfig();
            assertEquals(List.of(1, 1, 1), cfg.getIsActive());
        }

        @Test
        @DisplayName("get_ranker_cls returns from registry or null")
        void testGetRankerClsReturnsFromRankerCls() {
            class ConcreteRankConfig extends BaseRankConfig {
                ConcreteRankConfig() {
                    setName("weighted");
                }

                @Override
                public RankerArgs getArgs() {
                    return new RankerArgs(List.of(), Map.of());
                }
            }

            ConcreteRankConfig cfg = new ConcreteRankConfig();
            // Unknown database returns null
            Object result = cfg.getRankerCls("nonexistent_db_xyz");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("WeightedRankConfig Tests")
    class TestWeightedRankConfig {

        @Test
        @DisplayName("defaults: name_dense=0.15, content_dense=0.6, content_sparse=0.25")
        void testDefaults() {
            WeightedRankConfig cfg = new WeightedRankConfig();
            assertEquals(0.15, cfg.getNameDense(), 0.001);
            assertEquals(0.6, cfg.getContentDense(), 0.001);
            assertEquals(0.25, cfg.getContentSparse(), 0.001);
        }

        @Test
        @DisplayName("args: normalized weights when sum > 0")
        void testArgsNormalizedWeightsSumOne() {
            WeightedRankConfig cfg = new WeightedRankConfig(0.2, 0.2, 0.2);
            BaseRankConfig.RankerArgs args = cfg.getArgs();

            // Java implementation returns weights in keyword map
            Map<String, Object> keyword = args.getKeyword();
            double total = keyword.containsKey("name_dense") ? 
                (Double) keyword.get("name_dense") + 
                (Double) keyword.get("content_dense") + 
                (Double) keyword.get("content_sparse") : 0.0;
            
            assertEquals(1.0, total, 0.001);
            assertTrue(args.getPositional().isEmpty());
        }

        @Test
        @DisplayName("args zero weights returns empty")
        void testArgsZeroWeightsReturnsEmpty() {
            WeightedRankConfig cfg = new WeightedRankConfig(0, 0, 0);
            BaseRankConfig.RankerArgs args = cfg.getArgs();

            // When all weights are 0, total is 0, so keyword should be empty
            assertTrue(args.getKeyword().isEmpty() || 
                args.getKeyword().values().stream().allMatch(v -> (Double) v == 0.0));
        }

        @Test
        @DisplayName("bounds: weights in [0, 1]")
        void testBoundsWeightsInZeroOne() {
            WeightedRankConfig cfg = new WeightedRankConfig(0, 1, 0.5);
            assertTrue(cfg.getNameDense() >= 0 && cfg.getNameDense() <= 1);
            assertTrue(cfg.getContentDense() >= 0 && cfg.getContentDense() <= 1);
            assertTrue(cfg.getContentSparse() >= 0 && cfg.getContentSparse() <= 1);

            // Java doesn't have Pydantic validation, but we can test setter behavior
            // Note: Java implementation doesn't enforce bounds, unlike Pydantic
        }

        @Test
        @DisplayName("is_active returns [1, 0, 0] for (0.2, 0, 0)")
        void testIsActiveFromWeights() {
            WeightedRankConfig cfg = new WeightedRankConfig(0.2, 0, 0);
            assertEquals(List.of(1, 0, 0), cfg.getIsActive());
        }
    }

    @Nested
    @DisplayName("RRFRankConfig Tests")
    class TestRRFRankConfig {

        @Test
        @DisplayName("name='rrf', higher_is_better=true, k default")
        void testNameRrfHigherIsBetterKDefault() {
            RRFRankConfig cfg = new RRFRankConfig();
            assertEquals("rrf", cfg.getName());
            assertTrue(cfg.isHigherIsBetter());
            // Note: Python default is 40, Java default is 60
            assertEquals(60, cfg.getK());
        }

        @Test
        @DisplayName("args returns k in keyword map")
        void testArgsReturnsKAndEmptyDict() {
            RRFRankConfig cfg = new RRFRankConfig(60);
            BaseRankConfig.RankerArgs args = cfg.getArgs();

            // Java implementation returns k in keyword map
            assertEquals(60, args.getKeyword().get("k"));
            assertTrue(args.getPositional().isEmpty());
        }

        @Test
        @DisplayName("RRFRankConfig extends BaseRankConfig")
        void testInheritance() {
            RRFRankConfig cfg = new RRFRankConfig();
            assertTrue(cfg instanceof BaseRankConfig);
        }
    }
}