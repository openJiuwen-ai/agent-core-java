/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.ExtStaticCheckResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergedExtensionError;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergeRuntimeExtensionsResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.SkillPathKey;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.SourcePathKey;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.VerifiedExtensionTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock.MergeSuccessResult;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code MergeActivationBlock} helpers in
 * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
 *
 * <p>Mirrors Python's {@code TestMergeEvent}, {@code TestBuildMergeFixPrompt},
 * {@code TestMergeActivationBlock}, and {@code TestSchemaGate} in
 * {@code tests/unit_tests/auto_harness/stages/test_merge.py}.</p>
 */
class TestMergeStage {

    @TempDir
    private Path tempDir;

    @Test
    void metadataAndNameParsingMatchPythonShape() {
        MergeActivationBlock block = new MergeActivationBlock();

        assertThat(block.name()).isEqualTo("merge_ext");
        assertThat(MergeActivationBlock.parseNameOutput("office_ppt_generator"))
                .isEqualTo("office_ppt_generator");
        assertThat(MergeActivationBlock.parseNameOutput("推荐名称: report_writer_v2，原因..."))
                .isEqualTo("report_writer_v2");
        assertThat(MergeActivationBlock.parseNameOutput("BAD-NAME"))
                .isNull();
        assertThat(MergeActivationBlock.parseNameOutput(""))
                .isNull();
    }

    @Test
    void fallbackNamePrefersCapabilityBeforeConstraint() {
        ExtensionDesign constraint = ExtensionDesign.builder()
                .extensionName("security_guard")
                .kind("constraint")
                .build();
        ExtensionDesign capability = ExtensionDesign.builder()
                .extensionName("ppt_writer")
                .kind("capability")
                .build();

        assertThat(MergeActivationBlock.deriveMergedNameFallback(List.of()))
                .isEqualTo("merged_extensions");
        assertThat(MergeActivationBlock.deriveMergedNameFallback(List.of(capability)))
                .isEqualTo("ppt_writer");
        assertThat(MergeActivationBlock.deriveMergedNameFallback(List.of(constraint, capability)))
                .isEqualTo("ppt_writer_merged");
        assertThat(MergeActivationBlock.deriveMergedNameFallback(List.of(constraint)))
                .isEqualTo("security_guard");
    }

    @Test
    void mergeEventUsesActivateParentAndMergeExtensionStage() {
        OutputSchema event = MergeActivationBlock.mergeEvent("failed", "merged_ppt", 3, "ruff");

        assertThat(event.getType()).isEqualTo("stage_result");
        assertThat(event.getIndex()).isZero();
        Map<?, ?> payload = (Map<?, ?>) event.getPayload();
        assertThat(payload.get("stage")).isEqualTo("activate");
        assertThat(payload.get("parent_stage")).isEqualTo("activate");
        assertThat(payload.get("extension_stage")).isEqualTo("merge_ext");
        assertThat(payload.get("extension_name")).isEqualTo("merged_ppt");
        assertThat(payload.get("status")).isEqualTo("failed");
        assertThat(payload.get("repair_rounds")).isEqualTo(3);
        assertThat(payload.get("error")).isEqualTo("ruff");
        assertThat(payload.get("messages")).isEqualTo(List.of());
        assertThat(payload.get("metrics")).isEqualTo(Map.of());
    }

    @Test
    void runningEventMatchesPythonTestMergeEvent() {
        OutputSchema event = MergeActivationBlock.mergeEvent("running", "merged_extensions", 0, "");

        assertThat(event.getType()).isEqualTo("stage_result");
        Map<?, ?> payload = (Map<?, ?>) event.getPayload();
        assertThat(payload.get("extension_stage")).isEqualTo("merge_ext");
        assertThat(payload.get("extension_name")).isEqualTo("merged_extensions");
        assertThat(payload.get("status")).isEqualTo("running");
        assertThat(payload.get("repair_rounds")).isEqualTo(0);
    }

    @Test
    void failedEventCarriesErrorAndRepairRounds() {
        OutputSchema event = MergeActivationBlock.mergeEvent("failed", "merged_extensions", 3, "boom");

        Map<?, ?> payload = (Map<?, ?>) event.getPayload();
        assertThat(payload.get("status")).isEqualTo("failed");
        assertThat(payload.get("error")).isEqualTo("boom");
        assertThat(payload.get("repair_rounds")).isEqualTo(3);
    }

    @Test
    void successEventKeepsSuccessStatus() {
        OutputSchema event = MergeActivationBlock.mergeEvent("success", "", 0, "");

        Map<?, ?> payload = (Map<?, ?>) event.getPayload();
        assertThat(payload.get("status")).isEqualTo("success");
        assertThat(payload.get("extension_name")).isEqualTo("merged_extensions");
    }

    @Test
    void promptContainsRenameSummariesAndHardConstraints() {
        RuntimeExtensionArtifact merged = RuntimeExtensionArtifact.builder()
                .extensionName("merged_ppt")
                .runtimePath(tempDir.resolve("merged_ppt").toString())
                .configPath(tempDir.resolve("merged_ppt").resolve("harness_config.yaml").toString())
                .build();
        MergeRuntimeExtensionsResult mergeResult = new MergeRuntimeExtensionsResult(
                merged,
                Map.of(new SourcePathKey("src_ext", "tool.py"), "tool__src_ext.py"),
                Map.of(new SkillPathKey("src_ext", "write"), "write__src_ext"),
                List.of(Map.of("name", "src_ext", "description", "source"))
        );

        String prompt = MergeActivationBlock.buildMergeFixPrompt(
                merged,
                mergeResult,
                List.of("module import failed"),
                2,
                3
        );

        assertThat(prompt).contains("合并产物的静态校验失败");
        assertThat(prompt).contains("merged_ppt 根目录: " + merged.getRuntimePath());
        assertThat(prompt).contains("(src_ext, tool.py) -> tool__src_ext.py");
        assertThat(prompt).contains("(src_ext, write) -> write__src_ext");
        assertThat(prompt).contains("修复硬约束");
        assertThat(prompt).contains("openjiuwen.extensions.harness.merged_ppt");
        assertThat(prompt).contains("修复轮次: 2/3");
        assertThat(prompt).contains("module import failed");
    }

    @Test
    void promptContainsStaticErrorsAndAttemptFraction() {
        RuntimeExtensionArtifact merged = RuntimeExtensionArtifact.builder()
                .extensionName("merged_extensions")
                .runtimePath(tempDir.resolve("merged_extensions").toString())
                .configPath(tempDir.resolve("merged_extensions").resolve("harness_config.yaml").toString())
                .build();
        MergeRuntimeExtensionsResult mergeResult = new MergeRuntimeExtensionsResult(
                merged,
                Map.of(),
                Map.of(),
                List.of(Map.of("name", "ext_a"), Map.of("name", "ext_b"))
        );

        String prompt = MergeActivationBlock.buildMergeFixPrompt(
                merged,
                mergeResult,
                List.of("error A", "error B"),
                2,
                3
        );

        assertThat(prompt).contains("error A");
        assertThat(prompt).contains("error B");
        assertThat(prompt).contains("修复轮次: 2/3");
    }

    @Test
    void streamMergesStaticChecksCleansSourcesAndYieldsSuccessResult() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        Path sourceA = tempDir.resolve("source-a");
        Path sourceB = tempDir.resolve("source-b");
        Files.createDirectories(sourceA);
        Files.createDirectories(sourceB);
        RuntimeExtensionArtifact artifactA = RuntimeExtensionArtifact.builder()
                .extensionName("alpha_ext")
                .runtimePath(sourceA.toString())
                .configPath(sourceA.resolve("harness_config.yaml").toString())
                .build();
        RuntimeExtensionArtifact artifactB = RuntimeExtensionArtifact.builder()
                .extensionName("beta_ext")
                .runtimePath(sourceB.toString())
                .configPath(sourceB.resolve("harness_config.yaml").toString())
                .build();
        List<VerifiedExtensionTask> verifiedTasks = List.of(
                verifiedTask(orchestrator, "alpha_ext", artifactA),
                verifiedTask(orchestrator, "beta_ext", artifactB)
        );
        AtomicReference<String> mergedNameSeen = new AtomicReference<>();
        AtomicReference<String> sessionPrefixSeen = new AtomicReference<>();
        AtomicBoolean agentCreated = new AtomicBoolean(false);
        RuntimeExtensionArtifact merged = RuntimeExtensionArtifact.builder()
                .extensionName("package_bundle")
                .runtimePath(tempDir.resolve("package_bundle").toString())
                .configPath(tempDir.resolve("package_bundle").resolve("harness_config.yaml").toString())
                .build();
        MergeActivationBlock block = new MergeActivationBlock(
                (artifacts, sessionRoot, mergedName) -> {
                    mergedNameSeen.set(mergedName);
                    assertThat(artifacts).extracting(RuntimeExtensionArtifact::getExtensionName)
                            .containsExactly("alpha_ext", "beta_ext");
                    assertThat(sessionRoot).exists();
                    return new MergeRuntimeExtensionsResult(merged, Map.of(), Map.of(), List.of());
                },
                (runtimeExt, sessionIdPrefix) -> {
                    sessionPrefixSeen.set(sessionIdPrefix);
                    assertThat(runtimeExt).isSameAs(merged);
                    return new ExtStaticCheckResult(List.of(), 2, 1, 3, 1);
                },
                (ignoredConfig, workspace, rails) -> {
                    agentCreated.set(true);
                    return inputs -> List.of().iterator();
                },
                (ignoredConfig, prompt) -> "should_not_be_used"
        );

        List<Object> events = toList(block.stream(orchestrator, verifiedTasks, "package_bundle"));

        assertThat(mergedNameSeen.get()).isEqualTo("package_bundle");
        assertThat(sessionPrefixSeen.get()).startsWith("merge_");
        assertThat(agentCreated.get()).isFalse();
        assertThat(Files.exists(sourceA)).isFalse();
        assertThat(Files.exists(sourceB)).isFalse();
        assertThat(events).hasSize(3);
        assertThat(((OutputSchema) events.get(0)).getPayload().toString()).contains("running");
        MergeSuccessResult success = (MergeSuccessResult) events.get(1);
        assertThat(success.artifact()).isSameAs(merged);
        assertThat(((OutputSchema) events.get(2)).getPayload().toString()).contains("success");
    }

    @Test
    void oneRoundRepairCreatesAgentOnceThenSucceeds() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        RuntimeExtensionArtifact source = writeRuntimeExtension("repair_source");
        RuntimeExtensionArtifact merged = RuntimeExtensionArtifact.builder()
                .extensionName("merged_extensions")
                .runtimePath(tempDir.resolve("merged_extensions").toString())
                .configPath(tempDir.resolve("merged_extensions").resolve("harness_config.yaml").toString())
                .build();
        VerifiedExtensionTask task = verifiedTask(orchestrator, "repair_source", source);
        AtomicInteger staticChecks = new AtomicInteger();
        AtomicInteger agentStreams = new AtomicInteger();

        MergeActivationBlock block = new MergeActivationBlock(
                (artifacts, sessionRoot, mergedName) -> new MergeRuntimeExtensionsResult(merged, Map.of(), Map.of(), List.of()),
                (runtimeExt, sessionIdPrefix) -> staticChecks.incrementAndGet() == 1
                        ? new ExtStaticCheckResult(List.of("import error"), 0, 0, 0, 0)
                        : new ExtStaticCheckResult(List.of(), 1, 1, 0, 0),
                (ignoredConfig, workspace, rails) -> inputs -> {
                    agentStreams.incrementAndGet();
                    return List.of().iterator();
                },
                (ignoredConfig, prompt) -> "unused"
        );

        List<Object> events = toList(block.stream(orchestrator, List.of(task)));

        assertThat(staticChecks.get()).isEqualTo(2);
        assertThat(agentStreams.get()).isEqualTo(1);
        assertThat(events).anySatisfy(event -> assertThat(event).isInstanceOf(MergeSuccessResult.class));
    }

    @Test
    void exhaustedRepairRoundsThrowsAfterThreeAgentTurns() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        RuntimeExtensionArtifact source = writeRuntimeExtension("broken_source");
        RuntimeExtensionArtifact merged = RuntimeExtensionArtifact.builder()
                .extensionName("merged_extensions")
                .runtimePath(tempDir.resolve("merged_extensions").toString())
                .configPath(tempDir.resolve("merged_extensions").resolve("harness_config.yaml").toString())
                .build();
        VerifiedExtensionTask task = verifiedTask(orchestrator, "broken_source", source);
        AtomicInteger agentStreams = new AtomicInteger();

        MergeActivationBlock block = new MergeActivationBlock(
                (artifacts, sessionRoot, mergedName) -> new MergeRuntimeExtensionsResult(merged, Map.of(), Map.of(), List.of()),
                (runtimeExt, sessionIdPrefix) -> new ExtStaticCheckResult(List.of("always fails"), 0, 0, 0, 0),
                (ignoredConfig, workspace, rails) -> inputs -> {
                    agentStreams.incrementAndGet();
                    return List.of().iterator();
                },
                (ignoredConfig, prompt) -> "unused"
        );

        assertThatThrownBy(() -> block.stream(orchestrator, List.of(task)))
                .isInstanceOf(MergedExtensionError.class)
                .hasMessageContaining("static checks failed after 3 repair rounds");
        assertThat(agentStreams.get()).isEqualTo(3);
    }

    @Test
    void mergeHardErrorFailsFast() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        RuntimeExtensionArtifact source = writeRuntimeExtension("hard_error_source");
        VerifiedExtensionTask task = verifiedTask(orchestrator, "hard_error_source", source);

        MergeActivationBlock block = new MergeActivationBlock(
                (artifacts, sessionRoot, mergedName) -> {
                    throw new MergedExtensionError("bad manifest");
                },
                (runtimeExt, sessionIdPrefix) -> new ExtStaticCheckResult(List.of(), 0, 0, 0, 0),
                (ignoredConfig, workspace, rails) -> inputs -> List.of().iterator(),
                (ignoredConfig, prompt) -> "unused"
        );

        assertThatThrownBy(() -> block.stream(orchestrator, List.of(task)))
                .isInstanceOf(MergedExtensionError.class)
                .hasMessageContaining("bad manifest");
    }

    @Test
    void mergeEventPayloadHasRequiredSchemaFields() {
        OutputSchema event = MergeActivationBlock.mergeEvent("failed", "merged_extensions", 2, "test");
        Map<?, ?> payload = (Map<?, ?>) event.getPayload();

        assertThat(payload.containsKey("extension_stage")).isTrue();
        assertThat(payload.containsKey("extension_name")).isTrue();
        assertThat(payload.containsKey("status")).isTrue();
        assertThat(payload.containsKey("repair_rounds")).isTrue();
        assertThat(payload.containsKey("error")).isTrue();
        assertThat(payload.get("extension_stage")).isEqualTo("merge_ext");
        assertThat(payload.get("extension_name")).isEqualTo("merged_extensions");
    }

    private VerifiedExtensionTask verifiedTask(
            AutoHarnessOrchestrator orchestrator,
            String extensionName,
            RuntimeExtensionArtifact artifact
    ) {
        ExtensionDesign design = ExtensionDesign.builder()
                .extensionName(extensionName)
                .kind("capability")
                .build();
        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:" + extensionName)
                .build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        ctx.putArtifact("runtime_extension", artifact);
        return new VerifiedExtensionTask(design, task, ctx);
    }

    private RuntimeExtensionArtifact writeRuntimeExtension(String extensionName) throws Exception {
        Path root = Files.createDirectories(tempDir.resolve(extensionName));
        Files.writeString(root.resolve("harness_config.yaml"), "name: " + extensionName + "\n");
        return RuntimeExtensionArtifact.builder()
                .extensionName(extensionName)
                .runtimePath(root.toString())
                .configPath(root.resolve("harness_config.yaml").toString())
                .build();
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
