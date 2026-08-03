/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.ExtStaticCheckResult;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's verify_ext missing tests in
 * {@code tests/unit_tests/auto_harness/stages/test_verify_ext.py}.
 */
class VerifyExtPythonParityTest {

    @TempDir
    private Path tempDir;

    @Test
    void testVerifyExtLoadsGeneratedRuntimeExtension() throws Exception {
        TaskContext ctx = makeTaskContext();
        ExtensionBuildArtifact build = writeScaffold(ctx);
        ctx.putArtifact("extension_build", build);
        ExtendVerifyStage stage = new ExtendVerifyStage(
                ignored -> new ExtendVerifyStage.InstallResult(true, ""),
                (runtime, prefix) -> new ExtStaticCheckResult(List.of(), 1, 1, 0, 0),
                (taskContext, artifact, rails, tools, skills) -> new ExtendVerifyStage.AcceptanceRun(
                        List.of(),
                        new ExtendVerifyStage.CIResult(true, "")
                )
        );

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        assertThat(report.getCiResult()).containsEntry("passed", true);
        assertThat(report.getCiResult()).containsEntry("rails", 1);
        assertThat(report.getCiResult()).containsEntry("tools", 1);
    }

    @Test
    void testVerifyExtFailsWhenManifestMissing() throws Exception {
        TaskContext ctx = makeTaskContext();
        ExtensionBuildArtifact build = writeScaffold(ctx);
        Files.delete(Path.of(build.getConfigPath()));
        ctx.putArtifact("extension_build", build);
        ExtStaticCheckResult staticResult = new ExtStaticCheckResult(
                List.of("manifest_missing: harness_config.yaml"),
                0,
                0,
                0,
                0
        );
        ExtendVerifyStage stage = new ExtendVerifyStage(
                ignored -> new ExtendVerifyStage.InstallResult(true, ""),
                (runtime, prefix) -> staticResult,
                (taskContext, artifact, rails, tools, skills) -> new ExtendVerifyStage.AcceptanceRun(
                        List.of(),
                        new ExtendVerifyStage.CIResult(true, "")
                )
        );

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(result.getStatus()).isEqualTo("failed");
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(taskResult.isSuccess()).isFalse();
    }

    @Test
    void testVerifyExtRepairsManifestSchemaFailure() throws Exception {
        TaskContext ctx = makeTaskContext();
        ExtensionBuildArtifact build = writeScaffold(ctx);
        ctx.putArtifact("extension_build", build);
        CapturingAgent agent = new CapturingAgent();
        ctx.getRuntime().setTaskAgent(agent);
        AtomicInteger attempts = new AtomicInteger();
        ExtendVerifyStage stage = new ExtendVerifyStage(
                ignored -> new ExtendVerifyStage.InstallResult(true, ""),
                (runtime, prefix) -> attempts.getAndIncrement() == 0
                        ? new ExtStaticCheckResult(List.of("`description` 必须是字符串"), 0, 1, 0, 0)
                        : new ExtStaticCheckResult(List.of(), 0, 1, 0, 0),
                (taskContext, artifact, rails, tools, skills) -> new ExtendVerifyStage.AcceptanceRun(
                        List.of(),
                        new ExtendVerifyStage.CIResult(true, "")
                )
        );

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(result.getStatus()).isNotEqualTo("failed");
        assertThat(agent.prompts).hasSize(1);
        assertThat(agent.prompts.get(0)).contains("description` 必须是字符串");
    }

    @Test
    void testVerifyExtPromptRequiresArtifactLevelAcceptance() {
        ExtensionBuildArtifact build = ExtensionBuildArtifact.builder()
                .extensionName("huawei_ppt_generator")
                .extensionRoot(tempDir.resolve("huawei_ppt_generator").toString())
                .configPath(tempDir.resolve("huawei_ppt_generator").resolve("harness_config.yaml").toString())
                .build();

        String prompt = ExtendVerifyStage.buildExtAcceptanceTestPrompt(
                build,
                tempDir.resolve("test_huawei_ppt_generator.py"),
                "/usr/bin/python3",
                0,
                1,
                1,
                ""
        );

        assertThat(prompt).contains("文件产物验收 (仅文件生成类 Tool)");
        assertThat(prompt).contains("tmp_path");
        assertThat(prompt).contains("zipfile");
        assertThat(prompt).contains("ppt/presentation.xml");
        assertThat(prompt).contains("slide*.xml");
        assertThat(prompt).contains("禁止 JSON/Markdown 冒充");
        assertThat(prompt).contains("必须从 harness_config.yaml 实际声明的 module/class 获取");
        assertThat(prompt).contains("openjiuwen.extensions.harness.<extension_name>");
    }

    @Test
    void testVerifyExtFixPromptRejectsPlaceholderArtifacts() {
        ExtensionBuildArtifact build = ExtensionBuildArtifact.builder()
                .extensionName("huawei_ppt_generator")
                .extensionRoot(tempDir.resolve("huawei_ppt_generator").toString())
                .configPath(tempDir.resolve("huawei_ppt_generator").resolve("harness_config.yaml").toString())
                .build();

        String prompt = ExtendVerifyStage.buildExtAcceptanceFixPrompt(
                build,
                tempDir.resolve("test_huawei_ppt_generator.py"),
                "artifact_placeholder_output",
                "/usr/bin/python3"
        );

        assertThat(prompt).contains("禁止返回 JSON/Markdown 占位");
        assertThat(prompt).contains("禁止 JSON/Markdown 冒充");
        assertThat(prompt).contains("success=true");
    }

    @Test
    void testVerifyExtStaticFixPromptUsesManifestModules() {
        ExtensionBuildArtifact build = ExtensionBuildArtifact.builder()
                .extensionName("huawei_ppt_generator")
                .extensionRoot(tempDir.resolve("huawei_ppt_generator").toString())
                .configPath(tempDir.resolve("huawei_ppt_generator").resolve("harness_config.yaml").toString())
                .build();

        String prompt = ExtendVerifyStage.buildExtStaticFixPrompt(build, "module import failed");

        assertThat(prompt).contains("harness_config.yaml 中实际声明的 module/class");
        assertThat(prompt).contains("不要手写或猜测路径");
        assertThat(prompt).contains("openjiuwen.extensions.harness.<extension_name>.");
    }

    @Test
    void testVerifyExtReusesGeneratedTestAfterFix() throws Exception {
        TaskContext ctx = makeTaskContext();
        ExtensionBuildArtifact build = writeScaffold(ctx);
        CapturingAgent agent = new CapturingAgent();
        ctx.getRuntime().setTaskAgent(agent);
        List<String> seenTestContents = new ArrayList<>();
        AtomicInteger runCount = new AtomicInteger();

        ExtendVerifyStage.AcceptanceRun run = ExtendVerifyStage.runAgentGeneratedExtAcceptance(
                ctx,
                build,
                1,
                1,
                0,
                (pythonExecutable, testFile, cwd) -> {
                    try {
                        seenTestContents.add(Files.readString(testFile, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    return runCount.getAndIncrement() == 0
                            ? new ExtendVerifyStage.CIResult(false, "first failure")
                            : new ExtendVerifyStage.CIResult(true, "");
                }
        );

        assertThat(run.result().passed()).isTrue();
        assertThat(agent.promptKinds).containsExactly("generate", "fix");
        assertThat(seenTestContents).hasSize(2);
        assertThat(seenTestContents.get(0)).isEqualTo(seenTestContents.get(1));
    }

    @Test
    void testVerifyExtAgentTurnUsesFreshOpenSession() {
        SessionCapturingAgent agent = new SessionCapturingAgent();

        List<Object> chunks = toList(ExtendVerifyStage.streamVerifyExtAgentTurn(
                agent,
                "write tests",
                "verify-ext-test"
        ));

        assertThat(chunks).hasSize(1);
        assertThat(agent.seenSessions).hasSize(1);
        assertThat(agent.seenSessions.get(0)).startsWith("verify-ext-test-");
    }

    @Test
    void testVerifyExtScopesNestedAgentStageEvents() {
        OutputSchema chunk = new OutputSchema(
                "stage_result",
                0,
                Map.of("stage", "verify", "status", "success")
        );

        OutputSchema scoped = (OutputSchema) BaseStage.scopeOutputEventStage(chunk, "verify_ext");

        assertThat(((Map<?, ?>) scoped.getPayload()).get("stage")).isEqualTo("verify_ext");
        assertThat(((Map<?, ?>) chunk.getPayload()).get("stage")).isEqualTo("verify");
    }

    private TaskContext makeTaskContext() throws IOException {
        Path wtPath = tempDir.resolve("wt");
        Files.createDirectories(wtPath);
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(wtPath.toString());
        return new TaskContext(
                new AutoHarnessOrchestrator(config),
                OptimizationTask.builder().topic("verify ext").build(),
                runtime
        );
    }

    private ExtensionBuildArtifact writeScaffold(TaskContext ctx) throws IOException {
        Path ext = Path.of(ctx.getRuntime().getWtPath())
                .resolve("openjiuwen")
                .resolve("extensions")
                .resolve("harness")
                .resolve("demo_ext");
        Path railsDir = ext.resolve("rails");
        Path toolsDir = ext.resolve("tools");
        Files.createDirectories(railsDir);
        Files.createDirectories(toolsDir);
        Files.writeString(ext.resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(railsDir.resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(toolsDir.resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(railsDir.resolve("extension_rail.py"), "class ExtensionRail:\n    pass\n", StandardCharsets.UTF_8);
        Files.writeString(toolsDir.resolve("extension_tool.py"), "class ExtensionTool:\n    pass\n", StandardCharsets.UTF_8);
        Path manifest = ext.resolve("harness_config.yaml");
        Files.writeString(
                manifest,
                "schema_version: harness_config.v0.1\n"
                        + "name: demo_ext\n"
                        + "resources:\n"
                        + "  rails:\n"
                        + "    - type: package\n"
                        + "      module: openjiuwen.extensions.harness.demo_ext.rails.extension_rail\n"
                        + "      class: ExtensionRail\n"
                        + "  tools:\n"
                        + "    - type: package\n"
                        + "      module: openjiuwen.extensions.harness.demo_ext.tools.extension_tool\n"
                        + "      class: ExtensionTool\n",
                StandardCharsets.UTF_8
        );
        return ExtensionBuildArtifact.builder()
                .extensionName("demo_ext")
                .extensionRoot(ext.toAbsolutePath().normalize().toString())
                .configPath(manifest.toAbsolutePath().normalize().toString())
                .build();
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> events) {
        return (StageResult) events.get(events.size() - 1);
    }

    private static final class CapturingAgent extends DeepAgent {
        private final List<String> prompts = new ArrayList<>();
        private final List<String> promptKinds = new ArrayList<>();

        @Override
        public Iterator<Map<String, Object>> stream(Map<String, Object> inputs, AgentSessionApi session) {
            String query = String.valueOf(inputs.getOrDefault("query", ""));
            prompts.add(query);
            if (query.contains("测试文件必须写入: ")) {
                promptKinds.add("generate");
                Path testFile = Path.of(query.split("测试文件必须写入: ", 2)[1].split("\\R", 2)[0].strip());
                try {
                    Files.createDirectories(testFile.getParent());
                    Files.writeString(
                            testFile,
                            "# frozen acceptance test\n"
                                    + "def test_runtime_extension_acceptance():\n"
                                    + "    assert True\n",
                            StandardCharsets.UTF_8
                    );
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            } else if (query.contains("verify_ext 验收测试失败")) {
                promptKinds.add("fix");
            }
            return List.of(Map.of("type", "message", "payload", Map.of("content", "ok"))).iterator();
        }
    }

    private static final class SessionCapturingAgent extends DeepAgent {
        private final List<String> seenSessions = new ArrayList<>();

        @Override
        public Iterator<Map<String, Object>> stream(Map<String, Object> inputs, AgentSessionApi session) {
            assertThat(inputs.get("query")).isEqualTo("write tests");
            assertThat(session).isNotNull();
            assertThat(session).isInstanceOf(AgentSession.class);
            AgentSession agentSession = (AgentSession) session;
            assertThat(agentSession.getInner().streamWriterManager().streamEmitter().isClosed()).isFalse();
            seenSessions.add(session.getSessionId());
            Map<String, Object> chunk = Map.of("value", "chunk");
            return List.of(chunk).iterator();
        }
    }
}
