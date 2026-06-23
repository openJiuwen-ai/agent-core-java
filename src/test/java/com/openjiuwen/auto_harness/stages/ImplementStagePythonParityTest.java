/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.FixLoopResult;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's implement-stage helper tests in
 * {@code tests/unit_tests/auto_harness/stages/test_implement_stage.py}.
 */
class ImplementStagePythonParityTest {

    @TempDir
    private Path tempDir;

    @Test
    void testRunImplementStreamIncludesEditScope() {
        CapturingAgent agent = new CapturingAgent(List.of(message("ok")));
        OptimizationTask task = OptimizationTask.builder()
                .topic("restrict-scope")
                .description("只允许改 harness/core 与配套文件")
                .files(List.of("openjiuwen/harness/cli/ui/renderer.py"))
                .build();
        Experience related = Experience.builder()
                .type(ExperienceType.INSIGHT)
                .topic("scope")
                .summary("keep changes inside harness/core")
                .build();

        List<Object> chunks = ImplementStage.runImplementStream(agent, task, List.of(related), null, null);

        assertThat(chunks).hasSize(1);
        assertThat(agent.query).contains("`openjiuwen/harness/**`");
        assertThat(agent.query).contains("`openjiuwen/core/**`");
        assertThat(agent.query).contains("`openjiuwen/harness/cli/README.md`");
        assertThat(agent.query).contains("`tests/**`");
        assertThat(agent.query).contains("`examples/**`");
        assertThat(agent.query).contains("`docs/en/`");
        assertThat(agent.query).contains("`docs/zh/`");
        assertThat(agent.query).contains("范围外");
        assertThat(agent.query).contains("默认直接开始实施修改");
        assertThat(agent.query).contains("不要等待人工确认");
        assertThat(agent.query).contains("是否需要我开始实现");
    }

    @Test
    void testBuildPromptDebugStats() {
        Map<String, Integer> stats = ImplementStage.buildPromptDebugStats("line1\nline2");

        assertThat(stats).containsEntry("chars", 11).containsEntry("lines", 2).containsEntry("bytes", 11);
    }

    @Test
    void testRunImplementStreamManagesSessionLifecycle() {
        CapturingAgent agent = new CapturingAgent(List.of(message("ok")));
        Object session = new Object();
        OptimizationTask task = OptimizationTask.builder().topic("session-task").build();

        List<Object> chunks = ImplementStage.runImplementStream(agent, task, List.of(), session, null);

        assertThat(chunks).hasSize(1);
        assertThat(agent.query).contains("session-task");
    }

    @Test
    void testRunImplementStreamUsesSuppliedPrompt() {
        CapturingAgent agent = new CapturingAgent(List.of(message("ok")));
        OptimizationTask task = OptimizationTask.builder().topic("session-task").build();

        List<Object> chunks = ImplementStage.runImplementStream(agent, task, List.of(), null, "custom prompt");

        assertThat(chunks).hasSize(1);
        assertThat(agent.query).isEqualTo("custom prompt");
    }

    @Test
    void testStageEmitsReadyMessageBeforeAgentSummary() {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations(
                " M openjiuwen/harness/cli/cli.py",
                List.of()
        ));
        TaskContext ctx = taskContext(orchestrator, "补全 auto-harness 文档");
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(new OutputSchema(
                "llm_output",
                0,
                Map.of("content", "## 任务完成总结\n\n实现已完成。")
        ))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));

        assertThat(text(items.get(0))).isEqualTo("任务准备就绪: 补全 auto-harness 文档");
        assertThat(((OutputSchema) items.get(1)).getType()).isEqualTo("llm_output");
        assertThat(text(items.get(1))).contains("任务完成总结");
        assertThat(items.get(2)).isInstanceOf(StageResult.class);
        assertThat(((StageResult) items.get(2)).getMessages()).isEmpty();
    }

    @Test
    void testStageFailsWhenGitReportsNoRepoEdits() {
        TaskContext ctx = taskContext(orchestrator(new FakeGitOperations("", List.of())), "空跑实现");
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(new OutputSchema(
                "llm_output",
                0,
                Map.of("content", "done")
        ))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));
        StageResult result = (StageResult) items.get(items.size() - 1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("No allowed repo file was changed");
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void testStageFailsWithTaskFailedErrorBeforeGitCheck() {
        TaskContext ctx = taskContext(orchestrator(new FakeGitOperations(
                " M openjiuwen/harness/tools/filesystem.py",
                List.of("openjiuwen/harness/tools/filesystem.py")
        )), "模型超时");
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(Map.of(
                "type", "controller_output",
                "payload", Map.of(
                        "type", "task_failed",
                        "data", List.of(Map.of("text", "[181001] ReadTimeout"))
                )
        ))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));
        StageResult result = (StageResult) items.get(items.size() - 1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("ReadTimeout");
        assertThat(result.getError()).contains("Implement model call failed after");
        assertThat(result.getError()).contains("prompt_chars=");
        assertThat(result.getError()).contains("model_timeout_secs=300000.0");
        assertThat(result.getError()).doesNotContain("No allowed repo file was changed");
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void testStageIgnoresPreexistingDirtyFiles() {
        TaskContext ctx = taskContext(orchestrator(new FakeGitOperations(
                " M openjiuwen/harness/tools/filesystem.py",
                List.of()
        )), "预脏文件不算本轮改动");
        ctx.getRuntime().setPreexistingDirtyFiles(List.of("openjiuwen/harness/tools/filesystem.py"));
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(message("done"))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));
        StageResult result = (StageResult) items.get(items.size() - 1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("No allowed repo file was changed");
    }

    @Test
    void testStagePassesRuntimeTaskSessionToAgent() {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations(
                " M openjiuwen/harness/tools/filesystem.py",
                List.of()
        ));
        TaskContext ctx = taskContext(orchestrator, "session-aware implement");
        ctx.getRuntime().setTaskSession(new Object());
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(message("done"))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));
        StageResult result = (StageResult) items.get(items.size() - 1);

        assertThat(result.getStatus()).isEqualTo("success");
    }

    @Test
    void testStageUsesGitChangesEvenIfRailIsEmpty() {
        TaskContext ctx = taskContext(orchestrator(new FakeGitOperations(
                "",
                List.of("openjiuwen/harness/tools/filesystem.py")
        )), "git-diff 检测");
        ctx.getRuntime().setTaskAgent(new CapturingAgent(List.of(message("done"))));

        List<Object> items = toList(new MetaImplementStage().stream(ctx));
        StageResult result = (StageResult) items.get(items.size() - 1);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getArtifacts().get("code_change").toString())
                .contains("openjiuwen/harness/tools/filesystem.py");
    }

    @Test
    void testExtractRepoEditCandidatesToleratesStrippedStatusPrefix() {
        List<String> editedFiles = ImplementStage.extractRepoEditCandidates(
                "M openjiuwen/harness/tools/filesystem.py",
                List.of(),
                List.of()
        );

        assertThat(editedFiles).containsExactly("openjiuwen/harness/tools/filesystem.py");
    }

    @Test
    void testIterCiGateMessagesContainsSummaryAndExcerpt() {
        List<String> messages = VerifyStage.iterCiGateMessages(Map.of(
                "passed", false,
                "gates", List.of(
                        Map.of("name", "lint", "passed", false, "output", "E501 line too long"),
                        Map.of("name", "test", "passed", true, "output", "ok")
                ),
                "errors", ""
        ));

        assertThat(messages.get(0)).isEqualTo("CI 结果: lint=FAIL, test=PASS");
        assertThat(messages.get(1)).isEqualTo("[lint] E501 line too long");
    }

    @Test
    void testStartFixLoopEmitsProgressMessages() {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations("", List.of()));
        orchestrator.setCiGate(new FakeCIGateRunner(Map.of(
                "passed", false,
                "gates", List.of(Map.of("name", "lint", "passed", false, "output", "E501 line too long")),
                "errors", "E501 line too long"
        )));
        orchestrator.setFixLoop(new FixLoopController(1, 0, 600.0));
        CapturingAgent agent = new CapturingAgent(List.of(message("fixed")));
        TaskContext ctx = taskContext(orchestrator, "fix lint");
        ctx.getRuntime().setTaskAgent(agent);
        List<Object> events = new ArrayList<>();

        FixLoopResult result = MetaVerifyStage.startFixLoop(ctx, events);

        assertThat(result.isSuccess()).isFalse();
        List<String> texts = events.stream().map(ImplementStagePythonParityTest::text).toList();
        assertThat(texts).contains("[修复循环] 重跑 CI");
        assertThat(texts).contains("[修复循环] CI 结果: lint=FAIL");
        assertThat(texts).contains("[修复循环] [lint] E501 line too long");
        assertThat(texts).contains("[修复循环] 修复目标:\nE501 line too long");
        assertThat(agent.query).contains("E501 line too long");
    }

    @Test
    void testStartFixLoopOmitsWarningSummaryInFixTarget() {
        String failure = "AssertionError: expected value\nFAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x";
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations("", List.of()));
        orchestrator.setCiGate(new FakeCIGateRunner(Map.of(
                "passed", false,
                "gates", List.of(Map.of("name", "test", "passed", false, "output", failure)),
                "errors", failure
        )));
        orchestrator.setFixLoop(new FixLoopController(1, 0, 600.0));
        CapturingAgent agent = new CapturingAgent(List.of(message("fixed")));
        TaskContext ctx = taskContext(orchestrator, "fix pytest failure");
        ctx.getRuntime().setTaskAgent(agent);

        MetaVerifyStage.startFixLoop(ctx, new ArrayList<>());

        assertThat(agent.query).contains("AssertionError: expected value");
        assertThat(agent.query).doesNotContain("PydanticDeprecatedSince20");
    }

    private AutoHarnessOrchestrator orchestrator(FakeGitOperations git) {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .modelTimeoutSecs(300000.0)
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        orchestrator.setGit(git);
        return orchestrator;
    }

    private TaskContext taskContext(AutoHarnessOrchestrator orchestrator, String topic) {
        OptimizationTask task = OptimizationTask.builder()
                .topic(topic)
                .description("desc")
                .files(List.of("openjiuwen/core/foo.py"))
                .status(TaskStatus.RUNNING)
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(List.of());
        return new TaskContext(orchestrator, task, runtime);
    }

    private static OutputSchema message(String text) {
        return new OutputSchema("message", 0, Map.of("content", text));
    }

    private static String text(Object event) {
        Object source = event;
        if (event instanceof Map<?, ?> map && map.get("_output") != null) {
            source = map.get("_output");
        }
        if (source instanceof OutputSchema output && output.getPayload() instanceof Map<?, ?> payload) {
            Object content = payload.get("content");
            return String.valueOf(content == null ? "" : content);
        }
        if (source instanceof Map<?, ?> map && map.get("payload") instanceof Map<?, ?> payload) {
            Object content = payload.get("content");
            return String.valueOf(content == null ? "" : content);
        }
        return String.valueOf(source);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static final class CapturingAgent extends DeepAgent {
        private final List<Object> events;
        private String query = "";

        private CapturingAgent(List<Object> events) {
            this.events = events;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Map<String, Object>> stream(Map<String, Object> inputs) {
            query = String.valueOf(inputs.getOrDefault("query", ""));
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object event : events) {
                if (event instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                } else if (event instanceof OutputSchema output) {
                    result.add(Map.of("type", output.getType(), "payload", output.getPayload(), "_output", output));
                }
            }
            return result.iterator();
        }
    }

    private static final class FakeGitOperations extends GitOperations {
        private final String statusText;
        private final List<String> diffFiles;

        private FakeGitOperations(String statusText, List<String> diffFiles) {
            super("");
            this.statusText = statusText;
            this.diffFiles = diffFiles == null ? List.of() : diffFiles;
        }

        @Override
        public String statusPorcelain() {
            return statusText;
        }

        @Override
        public List<String> diffNameOnly(String revision) {
            return diffFiles;
        }
    }

    private static final class FakeCIGateRunner extends CIGateRunner {
        private final Map<String, Object> result;

        private FakeCIGateRunner(Map<String, Object> result) {
            super("");
            this.result = new LinkedHashMap<>(result);
        }

        @Override
        public CompletableFuture<Map<String, Object>> run(String action) {
            return CompletableFuture.completedFuture(result);
        }
    }
}
