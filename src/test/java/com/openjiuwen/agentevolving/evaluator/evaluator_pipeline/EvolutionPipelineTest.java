/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's evaluator pipeline tests for
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/pipeline.py}.
 */
class EvolutionPipelineTest {

    @BeforeEach
    void setUp() {
        AdapterRegistry.clearRegistriesForTesting();
        AdapterRegistry.registerAgent("fake-agent", FakeAgentAdapter.class);
        AdapterRegistry.registerBenchmark("fake-bench", FakeBenchAdapter.class);
        FakeAgentAdapter.reset();
        FakeBenchAdapter.reset();
    }

    @AfterEach
    void tearDown() {
        AdapterRegistry.clearRegistriesForTesting();
    }

    @Test
    void runSingleFiltersTasksAndWritesSummary(@TempDir Path tempDir) throws Exception {
        PipelineConfig config = baseConfig(tempDir);
        config.setTaskIds(List.of("task-2"));
        FakeBenchAdapter.tasks = List.of(
                task("task-1", false),
                task("task-2", false));
        FakeBenchAdapter.evalResult = new EvalResult(true, 1.0, "ok", 0, List.of(), Map.of());

        List<PipelineResult> results = new TestEvolutionPipeline(config).run().join();

        assertEquals(1, results.size());
        assertEquals("task-2", results.get(0).getTaskId());
        assertTrue(results.get(0).isConvergenceAchieved());
        assertEquals("single_pass", results.get(0).getConvergenceType());
        assertEquals(List.of("task-2"), FakeBenchAdapter.preparedTaskIds);
        assertEquals(1, FakeAgentAdapter.contexts.size());
        assertEquals(1, FakeAgentAdapter.contexts.get(0).getIteration());
        assertFalse(FakeAgentAdapter.contexts.get(0).isHasSkill());
        assertTrue(Files.exists(tempDir.resolve("task-2").resolve("agent_output.txt")));
        assertTrue(Files.exists(tempDir.resolve("task-2").resolve("iteration_001").resolve("summary.json")));
        assertTrue(Files.exists(tempDir.resolve("summary.json")));
    }

    @Test
    void runRecordsTaskExceptionAsErrorResult(@TempDir Path tempDir) {
        PipelineConfig config = baseConfig(tempDir);
        FakeBenchAdapter.tasks = List.of(task("task-1", false));
        FakeAgentAdapter.setupOk = false;

        List<PipelineResult> results = new TestEvolutionPipeline(config).run().join();

        assertEquals(1, results.size());
        assertEquals("task-1", results.get(0).getTaskId());
        assertEquals("error", results.get(0).getConvergenceType());
        assertEquals(0, results.get(0).getTotalIterations());
        assertTrue(String.valueOf(results.get(0).getMetrics().get("error")).contains("Agent setup failed"));
    }

    @Test
    void buildEvolutionSuggestionsIncludesFailuresAndSkillState() {
        IterationResult previous = new IterationResult();
        previous.setIteration(1);
        previous.setEvalResult(new EvalResult(false, 0.25, "", 1, List.of("test_a", "test_b"), Map.of()));
        previous.setSkillDelta(new SkillDelta(Map.of("skill-a", "content"), Map.of(), Map.of(), true));
        previous.setSkillChanged(false);

        String suggestions = EvolutionPipeline.buildEvolutionSuggestions(previous);

        assertTrue(suggestions.contains("Previous iteration pass rate: 25.0%"));
        assertTrue(suggestions.contains("Failed tests (2):"));
        assertTrue(suggestions.contains("Modified: skill-a"));
        assertTrue(suggestions.contains("The skill was NOT modified"));
    }

    @Test
    void computeEvolutionMetricsMirrorsPythonShape() {
        IterationResult first = iteration(1, 0.25, false, false);
        IterationResult second = iteration(2, 0.75, false, true);
        IterationResult third = iteration(3, 1.0, true, false);

        Map<String, Object> metrics = EvolutionPipeline.computeEvolutionMetrics(List.of(first, second, third));

        assertEquals(1.0, metrics.get("final_pass_rate"));
        assertEquals(1.0, metrics.get("best_pass_rate"));
        assertEquals(0.25, metrics.get("first_pass_rate"));
        assertEquals(0.75, metrics.get("improvement"));
        assertEquals(1L, metrics.get("skill_changes"));
        assertEquals(3, metrics.get("total_iterations"));
        assertEquals(true, metrics.get("converged"));
    }

    @Test
    void buildDefaultConfigUsesSingleRunAndEvolutionDefaults() {
        BuildConfigArgs args = new BuildConfigArgs();
        args.setTaskIds(List.of("t1"));
        args.setEvolutionMode(false);

        PipelineConfig single = EvolutionPipeline.buildDefaultConfig(args);
        assertEquals(Path.of("./single_run_results"), single.getResultsDir());
        assertEquals(1, single.getMaxIterations());
        assertEquals(List.of("t1"), single.getTaskIds());

        args.setEvolutionMode(true);
        args.setMaxIterations(7);
        PipelineConfig evolution = EvolutionPipeline.buildDefaultConfig(args);
        assertEquals(Path.of("./evolution_results"), evolution.getResultsDir());
        assertEquals(7, evolution.getMaxIterations());
        assertEquals(true, evolution.getAgentConfig().get("evolution_enabled"));
    }

    private static PipelineConfig baseConfig(Path resultsDir) {
        PipelineConfig config = new PipelineConfig();
        config.setAgent("fake-agent");
        config.setBenchmark("fake-bench");
        config.setResultsDir(resultsDir);
        config.setSaveTrajectory(true);
        return config;
    }

    private static Task task(String taskId, boolean hasSkills) {
        return new Task(taskId, "instruction", new LinkedHashMap<>(), new LinkedHashMap<>(), hasSkills, List.of());
    }

    private static IterationResult iteration(int iteration, double passRate, boolean passed, boolean skillChanged) {
        IterationResult result = new IterationResult();
        result.setIteration(iteration);
        result.setEvalResult(new EvalResult(passed, passRate, "", passed ? 0 : 1, List.of(), Map.of()));
        result.setAgentResult(new AgentRunResult());
        result.setSkillDelta(new SkillDelta());
        result.setSkillChanged(skillChanged);
        return result;
    }

    private static final class TestEvolutionPipeline extends EvolutionPipeline {

        private TestEvolutionPipeline(PipelineConfig config) {
            super(config);
        }

        @Override
        protected CompletableFuture<DockerEnvironment> createAndStartEnv(Task task) {
            return CompletableFuture.completedFuture(new NoOpDockerEnvironment());
        }
    }

    private static final class NoOpDockerEnvironment extends DockerEnvironment {

        private NoOpDockerEnvironment() {
            super("noop");
        }

        @Override
        public CompletableFuture<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeAgentAdapter extends BaseAgentAdapter {

        private static boolean setupOk = true;
        private static final List<AgentContext> contexts = new ArrayList<>();

        private FakeAgentAdapter(Map<String, Object> config) {
            super(config);
        }

        private static void reset() {
            setupOk = true;
            contexts.clear();
        }

        @Override
        public String name() {
            return "fake-agent";
        }

        @Override
        public List<String> supportedSkillsModes() {
            return List.of("create", "read");
        }

        @Override
        public CompletableFuture<Boolean> setup(DockerEnvironment env) {
            return CompletableFuture.completedFuture(setupOk);
        }

        @Override
        public CompletableFuture<AgentRunResult> run(DockerEnvironment env, Task task, AgentContext context) {
            contexts.add(context);
            AgentRunResult result = new AgentRunResult();
            result.setRawOutput("agent output for " + task.getTaskId());
            result.setTrajectory(List.of(Map.of("step", "run")));
            result.setTokensUsed(12);
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class FakeBenchAdapter extends BaseBenchAdapter {

        private static List<Task> tasks = List.of();
        private static EvalResult evalResult = new EvalResult(true, 1.0, "", 0, List.of(), Map.of());
        private static final List<String> preparedTaskIds = new ArrayList<>();

        private FakeBenchAdapter(Map<String, Object> config) {
            super(config);
        }

        private static void reset() {
            tasks = List.of();
            evalResult = new EvalResult(true, 1.0, "", 0, List.of(), Map.of());
            preparedTaskIds.clear();
        }

        @Override
        public String name() {
            return "fake-bench";
        }

        @Override
        public List<Task> loadTasks() {
            return tasks;
        }

        @Override
        public CompletableFuture<Void> prepareEnvironment(Task task, DockerEnvironment env) {
            preparedTaskIds.add(task.getTaskId());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<EvalResult> evaluate(DockerEnvironment env, Task task) {
            return CompletableFuture.completedFuture(evalResult);
        }
    }
}
