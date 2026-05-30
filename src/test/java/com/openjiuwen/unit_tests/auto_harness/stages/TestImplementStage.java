/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CodeChangeArtifact;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Implement stage helpers.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.stages.test_implement_stage}.</p>
 */
@DisplayName("Implement Stage Tests")
class TestImplementStage {

    @Nested
    @DisplayName("Implement Stage Helper Tests")
    class TestImplementStageHelpers {

        @Test
        void testRunImplementStreamIncludesEditScope() {
            PromptCapturingAgent agent = new PromptCapturingAgent();
            OptimizationTask task = new OptimizationTask("restrict-scope");
            task.setDescription("\u53ea\u5141\u8bb8\u6539 harness/core \u4e0e\u914d\u5957\u6587\u4ef6");
            task.setFiles(List.of("openjiuwen/harness/cli/ui/renderer.py"));
            Experience experience = new Experience();
            experience.setType(ExperienceType.INSIGHT);
            experience.setTopic("scope");
            experience.setSummary("keep changes inside harness/core");

            List<Object> chunks = collect(ImplementStage.runImplementStream(agent, task, List.of(experience)));

            assertEquals(1, chunks.size());
            assertTrue(agent.query.contains("`openjiuwen/harness/**`"));
            assertTrue(agent.query.contains("`openjiuwen/core/**`"));
            assertTrue(agent.query.contains("`openjiuwen/harness/cli/README.md`"));
            assertTrue(agent.query.contains("`tests/**`"));
            assertTrue(agent.query.contains("`examples/**`"));
            assertTrue(agent.query.contains("`docs/en/`"));
            assertTrue(agent.query.contains("`docs/zh/`"));
            assertTrue(agent.query.contains("\u8303\u56f4\u5916"));
            assertTrue(agent.query.contains("\u9ed8\u8ba4\u76f4\u63a5\u5f00\u59cb\u5b9e\u65bd\u4fee\u6539"));
            assertTrue(agent.query.contains("\u4e0d\u8981\u7b49\u5f85\u4eba\u5de5\u786e\u8ba4"));
            assertTrue(agent.query.contains("\u662f\u5426\u9700\u8981\u6211\u5f00\u59cb\u5b9e\u73b0"));
            assertNull(agent.session);
        }

        @Test
        void testBuildPromptDebugStats() {
            Map<String, Integer> stats = ImplementStage.buildPromptDebugStats("line1\nline2");

            assertEquals(Map.of("chars", 11, "lines", 2, "bytes", 11), stats);
        }

        @Test
        void testRunImplementStreamManagesSessionLifecycle() {
            PromptCapturingAgent agent = new PromptCapturingAgent();
            FakeSession session = new FakeSession();
            OptimizationTask task = new OptimizationTask("session-task");

            List<Object> chunks = collect(ImplementStage.runImplementStream(agent, task, List.of(), session, null));

            assertEquals(1, chunks.size());
            assertSame(session, agent.session);
            assertEquals(1, session.preRunCalls);
            assertEquals(Map.of("query", agent.query), session.preRunInputs);
            assertEquals(1, session.postRunCalls);
        }

        @Test
        void testRunImplementStreamUsesSuppliedPrompt() {
            PromptCapturingAgent agent = new PromptCapturingAgent();
            OptimizationTask task = new OptimizationTask("session-task");

            List<Object> chunks = collect(ImplementStage.runImplementStream(agent, task, List.of(), null, "custom prompt"));

            assertEquals(1, chunks.size());
            assertEquals("custom prompt", agent.query);
        }

        @Test
        void testStageEmitsReadyMessageBeforeAgentSummary() throws Exception {
            ImplementStage stage = new ImplementStage();
            StreamingAgent agent = new StreamingAgent();
            TaskContext ctx = context(
                    new OptimizationTask("\u8865\u5168 auto-harness \u6587\u6863"),
                    agent,
                    null,
                    " M openjiuwen/harness/cli/cli.py",
                    List.of(),
                    List.of()
            );

            List<Object> items = collect(stage.stream(ctx));

            assertOutput(items.get(0), "message", "\u4efb\u52a1\u51c6\u5907\u5c31\u7eea: \u8865\u5168 auto-harness \u6587\u6863");
            assertOutput(items.get(1), "message", "[1/5] \u6267\u884c\u4ee3\u7801\u4fee\u6539");
            assertOutputContains(items.get(2), "llm_output", "\u4efb\u52a1\u5b8c\u6210\u603b\u7ed3");
            assertNull(agent.session);
            StageResult result = assertInstanceOf(StageResult.class, items.get(3));
            assertEquals(List.of(), result.getMessages());
        }

        @Test
        void testStagePassesRuntimeTaskSessionToAgent() throws Exception {
            ImplementStage stage = new ImplementStage();
            StreamingAgent agent = new StreamingAgent();
            FakeSession session = new FakeSession();
            TaskContext ctx = context(
                    new OptimizationTask("session-aware implement"),
                    agent,
                    session,
                    " M openjiuwen/harness/tools/filesystem.py",
                    List.of(),
                    List.of()
            );

            List<Object> items = collect(stage.stream(ctx));

            StageResult result = assertInstanceOf(StageResult.class, items.get(items.size() - 1));
            assertEquals("success", result.getStatus());
            assertSame(session, agent.session);
            assertEquals(1, session.preRunCalls);
            assertEquals(1, session.postRunCalls);
        }

        @Test
        void testStageUsesGitChangesEvenIfRailIsEmpty() throws Exception {
            ImplementStage stage = new ImplementStage();
            TaskContext ctx = context(
                    new OptimizationTask("git-diff \u68c0\u6d4b"),
                    new StreamingAgent(),
                    null,
                    "",
                    List.of("openjiuwen/harness/tools/filesystem.py"),
                    List.of()
            );

            List<Object> items = collect(stage.stream(ctx));

            StageResult result = assertInstanceOf(StageResult.class, items.get(items.size() - 1));
            assertEquals("success", result.getStatus());
            CodeChangeArtifact artifact = (CodeChangeArtifact) result.getArtifacts().get("code_change");
            assertEquals(List.of("openjiuwen/harness/tools/filesystem.py"), artifact.getEditedFiles());
        }

        @Test
        void testStageFailsWithTaskFailedErrorBeforeGitCheck() throws Exception {
            OptimizationTask task = new OptimizationTask("\u6a21\u578b\u8d85\u65f6");
            ImplementStage stage = new ImplementStage();
            TaskContext ctx = context(
                    task,
                    new TaskFailedStreamingAgent(),
                    null,
                    " M openjiuwen/harness/tools/filesystem.py",
                    List.of("openjiuwen/harness/tools/filesystem.py"),
                    List.of()
            );

            List<Object> items = collect(stage.stream(ctx));

            StageResult result = assertInstanceOf(StageResult.class, items.get(items.size() - 1));
            assertEquals("failed", result.getStatus());
            assertTrue(result.getError().contains("ReadTimeout"));
            assertTrue(result.getError().contains("Implement model call failed after"));
            assertTrue(result.getError().contains("prompt_chars="));
            assertTrue(result.getError().contains("model_timeout_secs=300.0"));
            assertFalse(result.getError().contains("No allowed repo file was changed"));
            assertEquals("failed", task.getStatus().toString());
        }

        @Test
        void testStageFailsWhenGitReportsNoRepoEdits() throws Exception {
            OptimizationTask task = new OptimizationTask("\u7a7a\u8dd1\u5b9e\u73b0");
            ImplementStage stage = new ImplementStage();
            TaskContext ctx = context(task, new StreamingAgent(), null, "", List.of(), List.of());

            List<Object> items = collect(stage.stream(ctx));

            StageResult result = assertInstanceOf(StageResult.class, items.get(items.size() - 1));
            assertEquals("failed", result.getStatus());
            assertTrue(result.getError().contains("No allowed repo file was changed"));
            assertEquals("failed", task.getStatus().toString());
        }

        @Test
        void testExtractRepoEditCandidatesToleratesStrippedStatusPrefix() {
            List<String> editedFiles = ImplementStage.extractRepoEditCandidates(
                    "M openjiuwen/harness/tools/filesystem.py",
                    List.of()
            );

            assertEquals(List.of("openjiuwen/harness/tools/filesystem.py"), editedFiles);
        }

        @Test
        void testStageIgnoresPreexistingDirtyFiles() throws Exception {
            ImplementStage stage = new ImplementStage();
            TaskContext ctx = context(
                    new OptimizationTask("\u9884\u810f\u6587\u4ef6\u4e0d\u7b97\u672c\u8f6e\u6539\u52a8"),
                    new StreamingAgent(),
                    null,
                    " M openjiuwen/harness/tools/filesystem.py",
                    List.of(),
                    List.of("openjiuwen/harness/tools/filesystem.py")
            );

            List<Object> items = collect(stage.stream(ctx));

            StageResult result = assertInstanceOf(StageResult.class, items.get(items.size() - 1));
            assertEquals("failed", result.getStatus());
            assertTrue(result.getError().contains("No allowed repo file was changed"));
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

            assertEquals("CI \u7ed3\u679c: lint=FAIL, test=PASS", messages.get(0));
            assertEquals("[lint] E501 line too long", messages.get(1));
        }

        @Test
        void testStartFixLoopEmitsProgressMessages() {
            VerifyStage.FixLoopRun run = VerifyStage.startFixLoop(
                    new AutoHarnessConfig(),
                    new OptimizationTask("fix lint"),
                    null,
                    null,
                    new FakeCIGate(),
                    new FakeFixLoop(),
                    TestImplementStage::msg
            );

            assertFalse(run.ok());
            assertEquals(List.of("Phase 1 failed"), run.result().errorLog());
            List<String> texts = outputTexts(run.items());
            assertTrue(texts.contains("[\u4fee\u590d\u5faa\u73af] \u7b2c 1 \u6b21\u91cd\u8dd1 CI"));
            assertTrue(texts.contains("[\u4fee\u590d\u5faa\u73af] CI \u7ed3\u679c: lint=FAIL"));
            assertTrue(texts.contains("[\u4fee\u590d\u5faa\u73af] \u7b2c 1 \u6b21\u4fee\u590d"));
            assertTrue(texts.contains("[\u4fee\u590d\u5faa\u73af] \u4fee\u590d\u76ee\u6807:\nE501 line too long"));
            assertTrue(texts.contains("[\u4fee\u590d\u5faa\u73af] \u4fee\u590d\u8017\u5c3d"));
        }

        @Test
        void testStartFixLoopOmitsWarningSummaryInFixTarget() {
            VerifyStage.FixLoopRun run = VerifyStage.startFixLoop(
                    new AutoHarnessConfig(),
                    new OptimizationTask("fix pytest failure"),
                    null,
                    null,
                    new WarningOnlyDetailCIGate(),
                    new PassThroughFixLoop(),
                    TestImplementStage::msg
            );

            String joined = String.join("\n", outputTexts(run.items()));
            assertTrue(joined.contains("AssertionError: expected value"));
            assertFalse(joined.contains("PydanticDeprecatedSince20"));
        }
    }

    static class PromptCapturingAgent implements ImplementStage.ImplementAgent {
        String query = "";
        Object session;

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
            query = String.valueOf(inputs.get("query"));
            this.session = session;
            return List.of((Object) msg("ok")).iterator();
        }
    }

    static class StreamingAgent implements ImplementStage.ImplementAgent {
        Object session;

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
            this.session = session;
            return List.of((Object) new OutputSchema(
                    "llm_output",
                    0,
                    Map.of("content", "## \u4efb\u52a1\u5b8c\u6210\u603b\u7ed3\n\n\u5b9e\u73b0\u5df2\u5b8c\u6210\u3002")
            )).iterator();
        }
    }

    static class TaskFailedStreamingAgent implements ImplementStage.ImplementAgent {
        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
            return List.of((Object) new OutputSchema(
                    "controller_output",
                    0,
                    Map.of(
                            "type", "task_failed",
                            "data", List.of(Map.of(
                                    "type", "text",
                                    "text", "[181001] model call failed, reason: openAI API async stream error: ReadTimeout"
                            )),
                            "metadata", Map.of("task_id", "t1")
                    )
            )).iterator();
        }
    }

    static class FakeSession implements ImplementStage.ImplementSession {
        Map<String, Object> preRunInputs;
        int preRunCalls;
        int postRunCalls;

        @Override
        @SuppressWarnings("unchecked")
        public Object preRun(Map<String, Object> kwargs) {
            preRunCalls++;
            preRunInputs = (Map<String, Object>) kwargs.get("inputs");
            return this;
        }

        @Override
        public Object postRun() {
            postRunCalls++;
            return this;
        }
    }

    static class FakeCIGate implements VerifyStage.CIGate {
        @Override
        public Map<String, Object> run(String action) {
            return Map.of(
                    "passed", false,
                    "gates", List.of(Map.of("name", "lint", "passed", false, "output", "E501 line too long")),
                    "errors", "[lint]\nE501 line too long"
            );
        }
    }

    static class WarningOnlyDetailCIGate implements VerifyStage.CIGate {
        @Override
        public Map<String, Object> run(String action) {
            String output = "=================================== FAILURES ===================================\n"
                    + "E   AssertionError: expected value\n\n"
                    + "=========================== short test summary info ============================\n"
                    + "FAILED tests/unit_tests/core/foundation/tool/test_api_param_mapper.py::test_x";
            return Map.of(
                    "passed", false,
                    "gates", List.of(Map.of("name", "test", "passed", false, "output", output)),
                    "errors", "[test]\n" + output
            );
        }
    }

    static class FakeFixLoop implements VerifyStage.FixLoopControllerPort {
        @Override
        public VerifyStage.FixLoopResult run(
                java.util.function.Supplier<VerifyStage.CIResult> ciRunner,
                java.util.function.Consumer<String> agentFixer,
                java.util.function.Supplier<VerifyStage.EvalResult> evaluator) {
            ciRunner.get();
            agentFixer.accept("E501 line too long");
            return new VerifyStage.FixLoopResult(false, List.of("Phase 1 failed"));
        }
    }

    static class PassThroughFixLoop implements VerifyStage.FixLoopControllerPort {
        @Override
        public VerifyStage.FixLoopResult run(
                java.util.function.Supplier<VerifyStage.CIResult> ciRunner,
                java.util.function.Consumer<String> agentFixer,
                java.util.function.Supplier<VerifyStage.EvalResult> evaluator) {
            VerifyStage.CIResult ciResult = ciRunner.get();
            agentFixer.accept(ciResult.errors());
            return new VerifyStage.FixLoopResult(false, List.of("Phase 1 failed"));
        }
    }

    private static TaskContext context(
            OptimizationTask task,
            Object agent,
            ImplementStage.ImplementSession session,
            String statusText,
            List<String> diffFiles,
            List<String> preexistingDirtyFiles) throws Exception {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setModelTimeoutSecs(300.0);
        AutoHarnessOrchestrator orchestrator = AutoHarnessOrchestrator.createAutoHarnessOrchestrator(config);
        setGit(orchestrator, fakeGit(statusText, diffFiles));
        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(List.of());
        runtime.setWtPath("/tmp/worktree");
        runtime.setTaskAgent(agent);
        runtime.setTaskSession(session);
        runtime.setPreexistingDirtyFiles(preexistingDirtyFiles);
        return new TaskContext(orchestrator, task, runtime);
    }

    private static GitOperations fakeGit(String statusText, List<String> diffFiles) {
        return new GitOperations(
                ".",
                "",
                "develop",
                "",
                "openJiuwen",
                "agent-core",
                "",
                "",
                "",
                "",
                (command, cwd, env) -> {
                    if (command.contains("status")) {
                        return new GitOperations.CommandResult(0, statusText);
                    }
                    if (command.contains("diff") && command.contains("--name-only")) {
                        return new GitOperations.CommandResult(0, String.join("\n", diffFiles));
                    }
                    return new GitOperations.CommandResult(0, "");
                }
        );
    }

    private static void setGit(AutoHarnessOrchestrator orchestrator, GitOperations git) throws Exception {
        Field field = AutoHarnessOrchestrator.class.getDeclaredField("git");
        field.setAccessible(true);
        field.set(orchestrator, git);
    }

    private static OutputSchema msg(String text) {
        return new OutputSchema("message", 0, Map.of("content", text));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private static void assertOutput(Object item, String type, String content) {
        OutputSchema schema = assertInstanceOf(OutputSchema.class, item);
        assertEquals(type, schema.getType());
        assertEquals(content, ((Map<?, ?>) schema.getPayload()).get("content"));
    }

    private static void assertOutputContains(Object item, String type, String content) {
        OutputSchema schema = assertInstanceOf(OutputSchema.class, item);
        assertEquals(type, schema.getType());
        assertTrue(String.valueOf(((Map<?, ?>) schema.getPayload()).get("content")).contains(content));
    }

    private static List<String> outputTexts(List<Object> items) {
        List<String> texts = new ArrayList<>();
        for (Object item : items) {
            OutputSchema schema = (OutputSchema) item;
            texts.add(String.valueOf(((Map<?, ?>) schema.getPayload()).get("content")));
        }
        return texts;
    }
}
