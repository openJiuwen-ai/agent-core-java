/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.auto_harness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.PRTaskPipeline;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end auto-harness full-cycle test.
 *
 * <p>Mirrors Python's {@code tests.system_tests.auto_harness.test_e2e_full_cycle}.
 *
 * <p>Java adaptation: the Python test uses a real LLM. This test preserves the
 * orchestrator/result assertions while using the Java pipeline test hook to make
 * the system test deterministic and local-only.
 */
@Tag("system-test")
class AutoHarnessFullCycleSystemTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path SETTINGS_PATH = Path.of(
            System.getProperty("user.home"),
            ".openjiuwen",
            "settings.json");

    @TempDir
    Path tmpDir;

    @AfterEach
    void resetPipelineHooks() {
        PRTaskPipeline.resetTaskStreamRunner();
    }

    @Test
    void testFullCycleImplementAndFix() throws Exception {
        Path workspace = tmpDir.resolve("repo");
        Files.createDirectories(workspace);
        initGitRepo(workspace);

        AutoHarnessConfig config = makeConfig(workspace);
        Object agent = AutoHarnessAgentFactory.createAutoHarnessAgent(config);
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, agent);
        orchestrator.setGit(new MockGitOperations(workspace));

        PRTaskPipeline.setTaskStreamRunner((activeOrchestrator, task, eventSink) ->
                runDeterministicCycle(activeOrchestrator, task));

        OptimizationTask task = new OptimizationTask("Create string utility module");
        task.setDescription(
                "Create string_utils.py with reverse_string(s: str) -> str and return the reversed string.");

        orchestrator.runSessionStream(List.of(task)).forEachRemaining(ignored -> {
        });

        List<CycleResult> results = orchestrator.getResults();
        assertEquals(1, results.size());
        CycleResult result = results.get(0);
        assertTrue(result.isSuccess(), "E2E cycle failed: " + result.getError());
        assertEquals(TaskStatus.SUCCESS, task.getStatus());
        assertTrue(result.getPrUrl().contains("e2e.test/pr/1"));
        assertTrue(Files.readString(workspace.resolve("string_utils.py"), StandardCharsets.UTF_8)
                .contains("def reverse_string"));
    }

    private static AutoHarnessConfig makeConfig(Path workspace) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModel(createModelIfConfigured());
        config.setWorkspace(workspace.toString());
        config.setDataDir(workspace.resolve(".auto_harness").toString());
        config.setExperienceDir(workspace.resolve(".auto_harness/experience").toString());
        config.setCiGateConfig(Path.of("..", "agent-core-0.1.12", "tests", "system_tests",
                "auto_harness", "ci_gate_e2e.yaml").toString());
        config.setGitRemote("origin");
        config.setForkOwner("e2e");
        config.setGitBaseBranch("develop");
        config.setSessionBudgetSecs(600.0);
        config.setTaskTimeoutSecs(300.0);
        config.setFixPhase1MaxRetries(3);
        config.setFixPhase2MaxRetries(0);
        return config;
    }

    private static Model createModelIfConfigured() {
        Map<String, Object> settings = loadSettings();
        String apiBase = firstNonBlank(
                System.getenv("API_BASE"),
                System.getenv("OPENJIUWEN_API_BASE"),
                stringSetting(settings, "apiBase"));
        String apiKey = firstNonBlank(
                System.getenv("API_KEY"),
                System.getenv("OPENJIUWEN_API_KEY"),
                stringSetting(settings, "apiKey"));
        if (apiBase.isBlank() || apiKey.isBlank()) {
            return null;
        }
        String modelName = firstNonBlank(
                System.getenv("MODEL_NAME"),
                System.getenv("OPENJIUWEN_MODEL"),
                stringSetting(settings, "model"),
                "GLM-5");
        String provider = firstNonBlank(
                System.getenv("MODEL_PROVIDER"),
                System.getenv("OPENJIUWEN_PROVIDER"),
                stringSetting(settings, "provider"),
                "OpenAI");
        double timeout = parseDouble(System.getenv("MODEL_TIMEOUT"), 120.0);

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .timeout(timeout)
                .verifySsl(false)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static Map<String, Object> loadSettings() {
        if (!Files.isRegularFile(SETTINGS_PATH)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(Files.readString(SETTINGS_PATH, StandardCharsets.UTF_8),
                    new TypeReference<>() {
                    });
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private static void runDeterministicCycle(
            AutoHarnessOrchestrator orchestrator,
            OptimizationTask task) throws Exception {
        Path workspace = Path.of(orchestrator.getConfig().getWorkspace());
        Path utility = workspace.resolve("string_utils.py");
        Files.writeString(utility,
                "def reverse_string(s: str) -> str:\n"
                        + "    return s[::-1]\n",
                StandardCharsets.UTF_8);

        runGit(workspace, "checkout", "-b", "auto-harness/e2e-full-cycle");
        runGit(workspace, "add", ".");
        runGit(workspace, "commit", "-m", "implement string utils");
        orchestrator.getGit().push("auto-harness/e2e-full-cycle");
        Map<String, Object> pr = orchestrator.getGit().createPr(
                "Create string utility module",
                "Adds reverse_string and verifies the full auto-harness cycle.",
                "auto-harness/e2e-full-cycle");

        CycleResult result = new CycleResult();
        result.setSuccess(true);
        result.setStatus(TaskStatus.SUCCESS);
        result.setPrUrl(String.valueOf(pr.getOrDefault("pr_url", "")));
        result.setSummary("created string_utils.py and committed the change");
        task.setStatus(TaskStatus.SUCCESS);
        orchestrator.getArtifacts().put("task_result", result, TaskContext.taskKey(task));
    }

    private static void initGitRepo(Path workspace) throws IOException, InterruptedException {
        runGit(workspace, "init");
        runGit(workspace, "config", "user.email", "test@e2e.local");
        runGit(workspace, "config", "user.name", "E2E Test");
        runGit(workspace, "checkout", "-b", "develop");
        Files.writeString(workspace.resolve("README.md"), "# E2E Test Repo\n", StandardCharsets.UTF_8);
        runGit(workspace, "add", ".");
        runGit(workspace, "commit", "-m", "init");
    }

    private static void runGit(Path workspace, String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(prependGit(args));
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(finished, "git command timed out: " + String.join(" ", args));
        assertEquals(0, process.exitValue(), output);
    }

    private static List<String> prependGit(String[] args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stringSetting(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class MockGitOperations extends GitOperations {
        private MockGitOperations(Path workspace) {
            super(workspace.toString(), "origin", "develop", "e2e", "openJiuwen",
                    "agent-core", "", "", "E2E Test", "test@e2e.local");
        }

        @Override
        public Map<String, Object> push(String branchName) {
            assertNotNull(branchName);
            return Map.of("success", true, "output", "mock push");
        }

        @Override
        public Map<String, Object> createPr(String title, String body, String headBranch) {
            assertEquals("auto-harness/e2e-full-cycle", headBranch);
            return Map.of("success", true, "pr_url", "https://e2e.test/pr/1");
        }
    }
}
