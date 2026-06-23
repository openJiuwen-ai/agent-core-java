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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

/**
 * <p>Mirrors Python's {@code TestSkillsBenchAdapter*} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/test_skillsbench.py}.</p>
 * <p>Also exercises Python's {@code SkillsBenchAdapter} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/skillsbench.py}.</p>
 */
class SkillsBenchAdapterTest {

    @Test
    void defaultInitUsesPythonDefaults() throws Exception {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter();

        assertEquals("", fieldValue(adapter, "repoUrl"));
        assertEquals(Path.of("./skillsbench"), fieldValue(adapter, "repoPath"));
        assertEquals(Path.of("tasks"), fieldValue(adapter, "tasksDir"));
        assertEquals("/workspace", fieldValue(adapter, "workspaceDir"));
        assertEquals("with_skills", fieldValue(adapter, "skillsMode"));
    }

    @Test
    void initWithConfigOverridesDefaults() throws Exception {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of(
                "repo_url", "https://example.com/repo.git",
                "repo_path", "./custom_repo",
                "tasks_dir", "custom_tasks",
                "workspace_dir", "/custom/workspace",
                "skills_mode", "without_skills"));

        assertEquals("https://example.com/repo.git", fieldValue(adapter, "repoUrl"));
        assertEquals(Path.of("./custom_repo"), fieldValue(adapter, "repoPath"));
        assertEquals(Path.of("custom_tasks"), fieldValue(adapter, "tasksDir"));
        assertEquals("/custom/workspace", fieldValue(adapter, "workspaceDir"));
        assertEquals("without_skills", fieldValue(adapter, "skillsMode"));
    }

    @Test
    void nameReturnsSkillsBench() {
        assertEquals("skillsbench", new SkillsBenchAdapter().name());
    }

    @Test
    void cloneRepoReturnsTrueWhenNoRepoUrlConfigured() {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter();

        assertTrue(adapter.cloneRepo());
    }

    @Test
    void cloneRepoReturnsTrueWhenRepoPathAlreadyExists(@TempDir Path tempDir) throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        runGit(tempDir, "init", repoPath.toString());
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of(
                "repo_url", tempDir.resolve("origin.git").toString(),
                "repo_path", repoPath.toString()));

        assertTrue(adapter.cloneRepo());
    }

    @Test
    void cloneRepoReturnsTrueWhenLocalRepoCanBeCloned(@TempDir Path tempDir) throws Exception {
        Path originPath = tempDir.resolve("origin.git");
        Path clonePath = tempDir.resolve("clone");
        runGit(tempDir, "init", "--bare", originPath.toString());
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of(
                "repo_url", originPath.toString(),
                "repo_path", clonePath.toString()));

        assertTrue(adapter.cloneRepo());
        assertTrue(Files.isDirectory(clonePath.resolve(".git")));
    }

    @Test
    void cloneRepoReturnsFalseWhenCloneFails(@TempDir Path tempDir) {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of(
                "repo_url", tempDir.resolve("missing-origin").toString(),
                "repo_path", tempDir.resolve("clone").toString()));

        assertFalse(adapter.cloneRepo());
    }

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
    void loadTasksReturnsEmptyWhenTasksDirectoryDoesNotExist(@TempDir Path tempDir) {
        SkillsBenchAdapter adapter = new SkillsBenchAdapter(Map.of("tasks_dir", tempDir.resolve("missing").toString()));

        assertEquals(List.of(), adapter.loadTasks());
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

    @Test
    void calculatePassRateReturnsOneWhenAllTestsPassed() throws Exception {
        String output = "test1 passed\ntest2 passed\n2 passed";

        assertEquals(1.0, calculatePassRate(output));
    }

    @Test
    void calculatePassRateCountsFailedTests() throws Exception {
        String output = "test_example.py::test1 PASSED\ntest_example.py::test2 FAILED\n1 passed, 1 failed";

        assertEquals(0.5, calculatePassRate(output));
    }

    @Test
    void calculatePassRateCountsErrors() throws Exception {
        String output = "test_example.py::test1 PASSED\ntest_example.py::test2 ERROR\n1 passed, 1 error";

        assertEquals(0.5, calculatePassRate(output));
    }

    @Test
    void calculatePassRateReturnsZeroWhenNoTestsFound() throws Exception {
        assertEquals(0.0, calculatePassRate("No tests found"));
    }

    @Test
    void extractFailedTestsReturnsFailuresOnly() throws Exception {
        String output = """
                FAILED test_example.py::test_func1 - AssertionError
                PASSED test_example.py::test_func2
                FAILED test_example.py::test_func3 - ValueError
                """;

        List<String> result = extractFailedTests(output);

        assertTrue(result.contains("test_example.py::test_func1"));
        assertTrue(result.contains("test_example.py::test_func3"));
        assertFalse(result.contains("test_example.py::test_func2"));
    }

    @Test
    void extractFailedTestsReturnsErrors() throws Exception {
        String output = """
                ERROR test_example.py::test_setup - Exception
                PASSED test_example.py::test_func
                """;

        assertEquals(List.of("test_example.py::test_setup"), extractFailedTests(output));
    }

    @Test
    void extractFailedTestsReturnsEmptyWhenAllPassed() throws Exception {
        assertEquals(List.of(), extractFailedTests("All tests passed\n2 passed"));
    }

    private static Object fieldValue(SkillsBenchAdapter adapter, String name) throws Exception {
        Field field = SkillsBenchAdapter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(adapter);
    }

    private static double calculatePassRate(String output) throws Exception {
        Method method = SkillsBenchAdapter.class.getDeclaredMethod("calculatePassRate", String.class);
        method.setAccessible(true);
        return (double) method.invoke(null, output);
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractFailedTests(String output) throws Exception {
        Method method = SkillsBenchAdapter.class.getDeclaredMethod("extractFailedTests", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, output);
    }

    private static void runGit(Path workdir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "git command failed: " + String.join(" ", command));
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
