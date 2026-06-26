/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.benchmarks;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseBenchAdapter;

import java.util.List;

/**
 * Package bridge for evaluator-pipeline benchmark adapter exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.benchmarks}
 * in {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/__init__.py}.
 * </p>
 */
public final class BenchmarksPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/__init__.py";
    public static final Class<SkillsBenchAdapter> SKILLS_BENCH_ADAPTER = SkillsBenchAdapter.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("SkillsBenchAdapter");

    static {
        registerAll();
    }

    private BenchmarksPackage() {
    }

    public static void registerAll() {
        AdapterRegistry.registerBenchmark("skillsbench", SkillsBenchAdapter.class);
    }

    public static List<Class<? extends BaseBenchAdapter>> exportedBenchmarkAdapters() {
        registerAll();
        return List.<Class<? extends BaseBenchAdapter>>of(SkillsBenchAdapter.class);
    }
}
