/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters;

import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.BaseAgentAdapter;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.BaseBenchAdapter;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.agents.AgentsPackage;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.agents.JiuWenSwarmAgent;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.benchmarks.BenchmarksPackage;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.benchmarks.SkillsBenchAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for evaluator-pipeline adapter exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters}
 * in {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/__init__.py}.</p>
 */
public final class AdaptersPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/__init__.py";
    public static final Class<JiuWenSwarmAgent> JIU_WEN_SWARM_AGENT = JiuWenSwarmAgent.class;
    public static final Class<SkillsBenchAdapter> SKILLS_BENCH_ADAPTER = SkillsBenchAdapter.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("JiuWenSwarmAgent", "SkillsBenchAdapter");
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    static {
        registerAll();
    }

    private AdaptersPackage() {
    }

    public static void registerAll() {
        AgentsPackage.registerAll();
        BenchmarksPackage.registerAll();
    }

    public static List<Class<? extends BaseAgentAdapter>> exportedAgentAdapters() {
        registerAll();
        return AgentsPackage.exportedAgentAdapters();
    }

    public static List<Class<? extends BaseBenchAdapter>> exportedBenchmarkAdapters() {
        registerAll();
        return BenchmarksPackage.exportedBenchmarkAdapters();
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("JiuWenSwarmAgent", JiuWenSwarmAgent.class);
        exports.put("SkillsBenchAdapter", SkillsBenchAdapter.class);
        return Map.copyOf(exports);
    }
}
