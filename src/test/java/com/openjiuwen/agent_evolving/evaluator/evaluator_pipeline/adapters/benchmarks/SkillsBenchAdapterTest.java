/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.benchmarks;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.DockerEnvironment;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.EvalResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.ExecResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.Task;
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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillsBenchAdapterTest {

    @Test
    void loadTasksReadsInstructionAndSkillsMetadata(@TempDir Path tempDir) throws Exception {
        Path tasksDir = tempDir.resolve("tasks");
        Path taskDir = tasksDir.resolve("task-001");
        Files.createDirectories(taskDir.resolve("environment").resolve("skills").resolve("alpha"));
        Files.createDirectories(taskDir.resolve("environment").resolve("skills").resolve("beta"));
        Files.writeString(taskDir.resolve("instruction.md"), "Solve the benchmark", java.nio.charset.StandardCharsets.UTF_8);

        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of(
                "tasks_dir", tasksDir.toString(),
                "workspace_dir", "/workspace"));

        List<Task> tasks = adapter.loadTasks();

        assertEquals(1, tasks.size());
        Task task = tasks.get(0);
        assertEquals("task-001", task.getTaskId());
        assertTrue(task.isHasSkills());
        assertIterableEquals(List.of("alpha", "beta"), task.getSkills());
        assertEquals("docker", task.getEnvironmentSpec().get("type"));
        assertEquals("cd /workspace && bash tests/test.sh", task.getEnvironmentSpec().get("test_command"));
    }

    @Test
    void prepareEnvironmentCopiesExpectedFiles(@TempDir Path tempDir) throws Exception {
        Path taskDir = tempDir.resolve("task-002");
        Path testsDir = taskDir.resolve("tests");
        Path testsNestedDir = testsDir.resolve("cases");
        Path workspaceDir = taskDir.resolve("workspace");
        Path workspaceNestedDir = workspaceDir.resolve("fixtures");
        Path solutionDir = taskDir.resolve("solution");
        Path ignoredSolutionDir = solutionDir.resolve("nested");
        Files.createDirectories(testsNestedDir);
        Files.createDirectories(workspaceNestedDir);
        Files.createDirectories(ignoredSolutionDir);
        Files.writeString(taskDir.resolve("instruction.md"), "Run benchmark", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(testsDir.resolve("test.sh"), "echo test", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(testsNestedDir.resolve("sample.txt"), "sample", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(workspaceDir.resolve("input.json"), "{}", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(workspaceNestedDir.resolve("fixture.txt"), "fixture", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(solutionDir.resolve("answer.txt"), "answer", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(ignoredSolutionDir.resolve("ignored.txt"), "ignored", java.nio.charset.StandardCharsets.UTF_8);

        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of("workspace_dir", "/workspace"));
        RecordingDockerEnvironment env = new RecordingDockerEnvironment(new ExecResult("", "", 0, false));
        Task task = new Task(
                "task-002",
                "Run benchmark",
                Map.of("task_dir", taskDir.toString()),
                new LinkedHashMap<>(Map.of(
                        "task_dir", taskDir.toString(),
                        "tests_dir", testsDir.toString(),
                        "solution_dir", solutionDir.toString())),
                false,
                List.of());

        adapter.prepareEnvironment(task, env).join();

        assertTrue(env.commands.contains("mkdir -p /workspace"));
        assertTrue(env.commands.contains("ln -sf /workspace/tests /tests 2>/dev/null || true"));
        assertTrue(env.commands.contains("ln -sf /workspace/logs /logs 2>/dev/null || true"));
        assertTrue(env.copiedTargets.contains("/workspace/tests/test.sh"));
        assertTrue(env.copiedTargets.contains("/workspace/tests/cases/sample.txt"));
        assertTrue(env.copiedTargets.contains("/workspace/input.json"));
        assertTrue(env.copiedTargets.contains("/workspace/fixtures/fixture.txt"));
        assertTrue(env.copiedTargets.contains("/workspace/answer.txt"));
        assertTrue(env.copiedTargets.contains("/workspace/instruction.md"));
        assertFalse(env.copiedTargets.contains("/workspace/nested/ignored.txt"));
    }

    @Test
    void evaluateRewritesAbsolutePytestPathAndCalculatesPassRate() {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of("workspace_dir", "/workspace"));
        RecordingDockerEnvironment env = new RecordingDockerEnvironment(
                new ExecResult(
                        "2 passed, 1 failed\n",
                        "FAILED tests/test_outputs.py::test_case - AssertionError: boom\n",
                        1,
                        false));
        Task task = new Task(
                "task-003",
                "Eval",
                Map.of("verifier", Map.of("timeout_sec", 77)),
                new LinkedHashMap<>(Map.of("test_command", "pytest /tests/test_outputs.py -v")),
                false,
                List.of());

        EvalResult result = adapter.evaluate(env, task).join();

        assertEquals("pytest tests/test_outputs.py -v", env.lastCommand);
        assertEquals("/workspace", env.lastWorkdir);
        assertEquals(77, env.lastTimeout);
        assertEquals(2.0 / 3.0, result.getPassRate(), 1.0e-9);
        assertFalse(result.isPassed());
        assertEquals(List.of("tests/test_outputs.py::test_case"), result.getFailedTests());
    }

    private static final class RecordingDockerEnvironment extends DockerEnvironment {

        private final ExecResult execResult;
        private final List<String> commands = new ArrayList<>();
        private final List<String> copiedTargets = new ArrayList<>();
        private String lastCommand;
        private String lastWorkdir;
        private int lastTimeout;

        private RecordingDockerEnvironment(ExecResult execResult) {
            super("fake-image");
            this.execResult = execResult;
        }

        @Override
        public CompletableFuture<ExecResult> exec(String command, int commandTimeout, String workdir, Map<String, String> env) {
            this.commands.add(command);
            this.lastCommand = command;
            this.lastTimeout = commandTimeout;
            this.lastWorkdir = workdir;
            return CompletableFuture.completedFuture(execResult);
        }

        @Override
        public CompletableFuture<Boolean> copyTo(Path src, String dst) {
            this.copiedTargets.add(dst);
            return CompletableFuture.completedFuture(true);
        }
    }
}
