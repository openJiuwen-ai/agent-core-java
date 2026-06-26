/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.benchmarks;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseBenchAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/__init__.py}.
 */
class BenchmarksPackageTest {

    @Test
    void exposesPythonAllSymbols() {
        assertEquals(
                "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/__init__.py",
                BenchmarksPackage.PYTHON_MODULE);
        assertEquals(SkillsBenchAdapter.class, BenchmarksPackage.SKILLS_BENCH_ADAPTER);
        assertEquals(List.of("SkillsBenchAdapter"), BenchmarksPackage.EXPORTED_SYMBOLS);
        assertEquals(List.of(SkillsBenchAdapter.class), BenchmarksPackage.exportedBenchmarkAdapters());
    }

    @Test
    void registerAllKeepsSkillsbenchFactoryAvailable() {
        BenchmarksPackage.registerAll();

        assertTrue(AdapterRegistry.getRegisteredBenchmarkNames().contains("skillsbench"));
        BaseBenchAdapter adapter = AdapterRegistry.createBenchmark("skillsbench", Map.of());
        assertInstanceOf(SkillsBenchAdapter.class, adapter);
    }
}
