/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_evolving.evaluator.evaluator_pipeline.test_pipeline} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_pipeline.py}.
 */
class EvolutionPipelinePythonParityTest {

    @BeforeEach
    void setUp() {
        AdapterRegistry.clearRegistriesForTesting();
        AdapterRegistry.registerAgent("jiuwenswarm", RecordingAgentAdapter.class);
        AdapterRegistry.registerAgent("custom_agent", RecordingAgentAdapter.class);
        AdapterRegistry.registerBenchmark("skillsbench", RecordingBenchAdapter.class);
        AdapterRegistry.registerBenchmark("custom_bench", RecordingBenchAdapter.class);
        RecordingAgentAdapter.createdCount = 0;
        RecordingBenchAdapter.createdCount = 0;
    }

    @AfterEach
    void tearDown() {
        AdapterRegistry.clearRegistriesForTesting();
    }

    @Test
    void pipelineWithDefaultConfigCreatesAgentAndBench() {
        PipelineConfig config = new PipelineConfig();

        EvolutionPipeline pipeline = new EvolutionPipeline(config);

        assertThat(pipeline.getConfig()).isSameAs(config);
        assertThat(RecordingAgentAdapter.createdCount).isEqualTo(1);
        assertThat(RecordingBenchAdapter.createdCount).isEqualTo(1);
    }

    @Test
    void pipelineWithCustomConfigStoresOverrides() {
        PipelineConfig config = new PipelineConfig();
        config.setAgent("custom_agent");
        config.setBenchmark("custom_bench");
        config.setMaxIterations(5);
        config.setEvolutionMode(true);

        EvolutionPipeline pipeline = new EvolutionPipeline(config);

        assertThat(pipeline.getConfig().getAgent()).isEqualTo("custom_agent");
        assertThat(pipeline.getConfig().getMaxIterations()).isEqualTo(5);
        assertThat(pipeline.getConfig().isEvolutionMode()).isTrue();
    }

    @Test
    void computeEvolutionMetricsEmptyReturnsEmptyMap() {
        assertThat(EvolutionPipeline.computeEvolutionMetrics(List.of())).isEmpty();
    }

    @Test
    void computeEvolutionMetricsWithDataUsesFinalAndBestPassRate() {
        IterationResult first = iterationResult(1, false, 0.5);
        IterationResult second = iterationResult(2, true, 1.0);

        Map<String, Object> metrics = EvolutionPipeline.computeEvolutionMetrics(List.of(first, second));

        assertThat(metrics)
                .containsEntry("total_iterations", 2)
                .containsEntry("final_pass_rate", 1.0)
                .containsEntry("best_pass_rate", 1.0)
                .containsEntry("improvement", 0.5);
    }

    @Test
    void buildEvolutionSuggestionsPassedReportsNoChangesNeeded() {
        IterationResult previous = iterationResult(1, true, 1.0);

        String suggestions = EvolutionPipeline.buildEvolutionSuggestions(previous);

        assertThat(suggestions).contains("All tests passed");
        assertThat(suggestions).contains("No changes needed");
    }

    @Test
    void buildEvolutionSuggestionsFailedIncludesPassRateAndNoModificationHint() {
        IterationResult previous = iterationResult(1, false, 0.5);

        String suggestions = EvolutionPipeline.buildEvolutionSuggestions(previous);

        assertThat(suggestions.toLowerCase()).contains("pass rate");
        assertThat(suggestions).contains("NOT modified");
    }

    @Test
    void printSummaryWithEmptyResultsDoesNotThrow() {
        assertDoesNotThrow(() -> EvolutionPipeline.printSummary(List.of()));
    }

    @Test
    void printSummaryWithResultsDoesNotThrow() {
        PipelineResult result = new PipelineResult();
        result.setTaskId("task1");
        result.setAgentName("test_agent");
        result.setBenchmarkName("test_bench");
        result.setTotalIterations(2);
        result.setConvergenceAchieved(true);
        result.setMetrics(Map.of("score", 0.8));
        result.setOutputDir(Path.of("./results"));

        assertDoesNotThrow(() -> EvolutionPipeline.printSummary(List.of(result)));
    }

    @Test
    void createAgentDefaultReturnsRegisteredAdapter() {
        BaseAgentAdapter agent = AdapterRegistry.createAgent("jiuwenswarm", Map.of("model", "test-model"));

        assertThat(agent).isInstanceOf(RecordingAgentAdapter.class);
        assertThat(agent.getConfig()).containsEntry("model", "test-model");
    }

    @Test
    void createBenchDefaultReturnsRegisteredAdapter() {
        BaseBenchAdapter bench = EvolutionPipeline.createBench("skillsbench", Map.of("data_path", "./data"));

        assertThat(bench).isInstanceOf(RecordingBenchAdapter.class);
        assertThat(bench.getConfig()).containsEntry("data_path", "./data");
    }

    private static IterationResult iterationResult(int iteration, boolean passed, double passRate) {
        IterationResult result = new IterationResult();
        result.setIteration(iteration);
        result.setAgentResult(new AgentRunResult());
        result.setEvalResult(new EvalResult(passed, passRate, "", passed ? 0 : 1, List.of(), Map.of()));
        result.setSkillDelta(new SkillDelta());
        return result;
    }

    static class RecordingAgentAdapter extends BaseAgentAdapter {

        private static int createdCount;

        RecordingAgentAdapter(Map<String, Object> config) {
            super(config);
            createdCount++;
        }

        @Override
        public String name() {
            return "jiuwenswarm";
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

    static class RecordingBenchAdapter extends BaseBenchAdapter {

        private static int createdCount;

        RecordingBenchAdapter(Map<String, Object> config) {
            super(config);
            createdCount++;
        }

        @Override
        public String name() {
            return "skillsbench";
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
