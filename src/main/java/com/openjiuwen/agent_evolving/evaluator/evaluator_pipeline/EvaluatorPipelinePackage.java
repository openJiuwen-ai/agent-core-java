/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for evaluator pipeline exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator_pipeline} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/__init__.py}.</p>
 */
public final class EvaluatorPipelinePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Task",
            "EvalResult",
            "SkillDelta",
            "IterationResult",
            "PipelineResult",
            "AgentContext",
            "AgentRunResult",
            "ExecResult",
            "PipelineConfig",
            "DockerEnvironment",
            "BaseAgentAdapter",
            "BaseBenchAdapter",
            "SkillManager",
            "EvolutionPipeline",
            "create_agent",
            "create_bench"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private EvaluatorPipelinePackage() {
    }

    public static BaseAgentAdapter createAgent(String name, Map<String, Object> config) {
        return AdapterRegistry.createAgent(name, config == null ? Map.of() : new LinkedHashMap<>(config));
    }

    public static BaseBenchAdapter createBench(String name, Map<String, Object> config) {
        return AdapterRegistry.createBenchmark(name, config == null ? Map.of() : new LinkedHashMap<>(config));
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("Task", Task.class);
        exports.put("EvalResult", EvalResult.class);
        exports.put("SkillDelta", SkillDelta.class);
        exports.put("IterationResult", IterationResult.class);
        exports.put("PipelineResult", PipelineResult.class);
        exports.put("AgentContext", AgentContext.class);
        exports.put("AgentRunResult", AgentRunResult.class);
        exports.put("ExecResult", ExecResult.class);
        exports.put("PipelineConfig", PipelineConfig.class);
        exports.put("DockerEnvironment", DockerEnvironment.class);
        exports.put("BaseAgentAdapter", BaseAgentAdapter.class);
        exports.put("BaseBenchAdapter", BaseBenchAdapter.class);
        exports.put("SkillManager", SkillManager.class);
        exports.put("EvolutionPipeline", EvolutionPipeline.class);
        return Map.copyOf(exports);
    }
}
