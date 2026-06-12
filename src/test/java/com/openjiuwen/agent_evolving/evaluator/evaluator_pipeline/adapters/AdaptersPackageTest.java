/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseAgentAdapter;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseBenchAdapter;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents.JiuWenSwarmAgent;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.benchmarks.SkillsBenchAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/__init__.py}.
 */
class AdaptersPackageTest {

    @Test
    void exposesPythonAllSymbols() {
        assertEquals(
                "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/__init__.py",
                AdaptersPackage.PYTHON_MODULE);
        assertEquals(JiuWenSwarmAgent.class, AdaptersPackage.JIU_WEN_SWARM_AGENT);
        assertEquals(SkillsBenchAdapter.class, AdaptersPackage.SKILLS_BENCH_ADAPTER);
        assertEquals(List.of("JiuWenSwarmAgent", "SkillsBenchAdapter"), AdaptersPackage.EXPORTED_SYMBOLS);
        assertEquals(JiuWenSwarmAgent.class, AdaptersPackage.EXPORTED_TYPES.get("JiuWenSwarmAgent"));
        assertEquals(SkillsBenchAdapter.class, AdaptersPackage.EXPORTED_TYPES.get("SkillsBenchAdapter"));
    }

    @Test
    void delegatesRegistrationToChildPackages() {
        AdaptersPackage.registerAll();

        assertTrue(AdapterRegistry.getRegisteredAgentNames().contains("jiuwenswarm"));
        assertTrue(AdapterRegistry.getRegisteredBenchmarkNames().contains("skillsbench"));

        BaseAgentAdapter agent = AdapterRegistry.createAgent("jiuwenswarm", Map.of());
        BaseBenchAdapter benchmark = AdapterRegistry.createBenchmark("skillsbench", Map.of());

        assertInstanceOf(JiuWenSwarmAgent.class, agent);
        assertInstanceOf(SkillsBenchAdapter.class, benchmark);
        assertEquals(List.of(JiuWenSwarmAgent.class), AdaptersPackage.exportedAgentAdapters());
        assertEquals(List.of(SkillsBenchAdapter.class), AdaptersPackage.exportedBenchmarkAdapters());
    }
}
