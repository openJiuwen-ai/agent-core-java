/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's package export surface in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/__init__.py}.
 */
class EvaluatorPipelinePackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertThat(EvaluatorPipelinePackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_evolving/evaluator/evaluator_pipeline/__init__.py");
        assertThat(EvaluatorPipelinePackage.EXPORTED_SYMBOLS).containsExactly(
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
        assertThat(EvaluatorPipelinePackage.EXPORTED_TYPES)
                .containsEntry("Task", Task.class)
                .containsEntry("EvolutionPipeline", EvolutionPipeline.class)
                .containsEntry("BaseAgentAdapter", BaseAgentAdapter.class)
                .containsEntry("BaseBenchAdapter", BaseBenchAdapter.class);
    }

    @Test
    void functionExportsCreateRegisteredAdapters() {
        AdapterRegistry.clearRegistriesForTesting();
        AdapterRegistry.registerAgent("unit-agent", UnitAgentAdapter.class);
        AdapterRegistry.registerBenchmark("unit-bench", UnitBenchAdapter.class);

        BaseAgentAdapter agent = EvaluatorPipelinePackage.createAgent("unit-agent", Map.of("model", "test"));
        BaseBenchAdapter bench = EvaluatorPipelinePackage.createBench("unit-bench", Map.of("tasks", "none"));

        assertThat(agent).isInstanceOf(UnitAgentAdapter.class);
        assertThat(agent.getConfig()).containsEntry("model", "test");
        assertThat(bench).isInstanceOf(UnitBenchAdapter.class);
        assertThat(bench.getConfig()).containsEntry("tasks", "none");
    }

    static class UnitAgentAdapter extends BaseAgentAdapter {

        UnitAgentAdapter(Map<String, Object> config) {
            super(config);
        }

        @Override
        public String name() {
            return "unit-agent";
        }

        @Override
        public List<String> supportedSkillsModes() {
            return List.of();
        }

        @Override
        public CompletableFuture<Boolean> setup(DockerEnvironment env) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<AgentRunResult> run(DockerEnvironment env, Task task, AgentContext context) {
            return CompletableFuture.completedFuture(new AgentRunResult());
        }
    }

    static class UnitBenchAdapter extends BaseBenchAdapter {

        UnitBenchAdapter(Map<String, Object> config) {
            super(config);
        }

        @Override
        public String name() {
            return "unit-bench";
        }

        @Override
        public List<Task> loadTasks() {
            return List.of();
        }

        @Override
        public CompletableFuture<Void> prepareEnvironment(Task task, DockerEnvironment env) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<EvalResult> evaluate(DockerEnvironment env, Task task) {
            return CompletableFuture.completedFuture(new EvalResult());
        }
    }
}
