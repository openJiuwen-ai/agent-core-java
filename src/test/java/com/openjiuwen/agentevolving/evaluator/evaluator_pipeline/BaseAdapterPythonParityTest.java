/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_evolving.evaluator.evaluator_pipeline.test_base} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_base.py}.
 */
class BaseAdapterPythonParityTest {

    @Test
    void defaultModelReturnsNull() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        assertThat(adapter.defaultModel()).isNull();
    }

    @Test
    void validateConfigReturnsEmptyList() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        assertThat(adapter.validateConfig()).isEmpty();
    }

    @Test
    void logsDirRaisesWhenNotSet() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        assertThatThrownBy(adapter::getLogsDir)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("logs_dir not set");
    }

    @Test
    void setLogsDirCreatesDirectory(@TempDir Path tempDir) {
        TestAgentAdapter adapter = new TestAgentAdapter();
        Path logsPath = tempDir.resolve("logs");

        adapter.setLogsDir(logsPath);

        assertThat(Files.isDirectory(logsPath)).isTrue();
        assertThat(adapter.getLogsDir()).isEqualTo(logsPath);
    }

    @Test
    void loadSkillsReturnsZero() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        Integer result = adapter.loadSkills(null, Map.of("skill1", "content"), Map.of(), Map.of()).join();

        assertThat(result).isZero();
    }

    @Test
    void captureSkillsReturnsEmptyDelta() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        SkillDelta result = adapter.captureSkills(null).join();

        assertThat(result.getSkills()).isEmpty();
    }

    @Test
    void getSourceFilesReturnsNull() {
        TestAgentAdapter adapter = new TestAgentAdapter();

        assertThat(adapter.getSourceFiles()).isNull();
    }

    @Test
    void cloneRepoReturnsTrue() {
        TestBenchAdapter bench = new TestBenchAdapter();

        assertThat(bench.cloneRepo()).isTrue();
    }

    @Test
    void taskBasePathReturnsEmptyString() {
        TestBenchAdapter bench = new TestBenchAdapter();

        assertThat(bench.taskBasePath()).isEmpty();
    }

    @Test
    void filterTasksReturnsAllTasksWhenNoFiltersProvided() {
        TestBenchAdapter bench = new TestBenchAdapter();
        List<Task> tasks = List.of(task("task1", "Test1"), task("task2", "Test2"));

        List<Task> filtered = bench.filterTasks(tasks, null, null, null);

        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).getTaskId()).isEqualTo("task1");
    }

    @Test
    void filterTasksByTaskIds() {
        TestBenchAdapter bench = new TestBenchAdapter();
        List<Task> tasks = List.of(task("task1", "Test1"), task("task2", "Test2"), task("task3", "Test3"));

        List<Task> filtered = bench.filterTasks(tasks, List.of("task1", "task3"), null, null);

        assertThat(filtered).extracting(Task::getTaskId).containsExactly("task1", "task3");
    }

    @Test
    void filterTasksByCategories() {
        TestBenchAdapter bench = new TestBenchAdapter();
        List<Task> tasks = List.of(
                task("task1", "Test1", "category", "cat1"),
                task("task2", "Test2", "category", "cat2"),
                task("task3", "Test3", "category", "cat1")
        );

        List<Task> filtered = bench.filterTasks(tasks, null, List.of("cat1"), null);

        assertThat(filtered).extracting(Task::getTaskId).containsExactly("task1", "task3");
    }

    @Test
    void filterTasksByDifficulties() {
        TestBenchAdapter bench = new TestBenchAdapter();
        List<Task> tasks = List.of(
                task("task1", "Test1", "difficulty", "easy"),
                task("task2", "Test2", "difficulty", "hard"),
                task("task3", "Test3", "difficulty", "medium")
        );

        List<Task> filtered = bench.filterTasks(tasks, null, null, List.of("easy", "medium"));

        assertThat(filtered).extracting(Task::getTaskId).containsExactly("task1", "task3");
    }

    @Test
    void aggregateReturnsDefaultsForEmptyResults() {
        TestBenchAdapter bench = new TestBenchAdapter();

        Map<String, Object> result = bench.aggregate(List.of());

        assertThat(result)
                .containsEntry("overall_score", 0.0)
                .containsEntry("passed", 0)
                .containsEntry("total", 0);
    }

    @Test
    void aggregateCalculatesStatistics() {
        TestBenchAdapter bench = new TestBenchAdapter();

        Map<String, Object> result = bench.aggregate(List.of(
                evalResult(true, 1.0),
                evalResult(false, 0.0),
                evalResult(true, 1.0)
        ));

        assertThat(result.get("total")).isEqualTo(3);
        assertThat(result.get("passed")).isEqualTo(2L);
        assertThat((Double) result.get("overall_score")).isEqualTo(2.0 / 3);
    }

    private static Task task(String taskId, String instruction) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setInstruction(instruction);
        return task;
    }

    private static Task task(String taskId, String instruction, String metadataKey, String metadataValue) {
        Task task = task(taskId, instruction);
        task.setMetadata(Map.of(metadataKey, metadataValue));
        return task;
    }

    private static EvalResult evalResult(boolean passed, double passRate) {
        EvalResult result = new EvalResult();
        result.setPassed(passed);
        result.setPassRate(passRate);
        return result;
    }

    private static final class TestAgentAdapter extends BaseAgentAdapter {

        @Override
        public String name() {
            return "test";
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

    private static final class TestBenchAdapter extends BaseBenchAdapter {

        @Override
        public String name() {
            return "test";
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
