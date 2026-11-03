/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesignArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock.MergeSuccessResult;
import com.openjiuwen.auto_harness.stages.SessionStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ExtendedEvolvePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extended_evolve_pipeline.py}.
 */
class ExtendedEvolvePipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void metadataMatchesPythonPipelineConstants() {
        ExtendedEvolvePipeline pipeline = new ExtendedEvolvePipeline();

        assertThat(pipeline.name()).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(pipeline.description()).isEqualTo("Extended evolve generation pipeline.");
        assertThat(pipeline.expectedOutputs()).containsExactly("extension_design", "session_results");
        assertThat(pipeline.stageOrder())
                .extracting(ExtendedEvolvePipeline.StageOrderEntry::slot)
                .containsExactly("assess", "plan", "build_verify", "activate");
        assertThat(pipeline.stageMap().getMapping()).containsKeys("assess", "plan");
    }

    @Test
    void dependencyHelpersMatchPythonSetRules() {
        ExtensionDesign design = ExtensionDesign.builder()
                .extensionName("child")
                .dependsOn(List.of("parent", "missing"))
                .build();

        assertThat(ExtendedEvolvePipeline.hasUnmetSelectedDependency(
                design,
                Set.of("parent"),
                Set.of("child", "parent")
        )).isTrue();
        assertThat(ExtendedEvolvePipeline.collectUnmetSelectedDependencies(
                design,
                Set.of("parent"),
                Set.of("child", "parent")
        )).containsExactly("parent", "missing");
        assertThat(ExtendedEvolvePipeline.dependenciesCompleted(design, Set.of("parent"))).isFalse();
        assertThat(ExtendedEvolvePipeline.collectIncompleteDependencies(design, Set.of("parent")))
                .containsExactly("missing");
    }

    @Test
    void recordSkippedDependencyAddsFailedCycleResult() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        SessionContext ctx = new SessionContext(orchestrator);
        ExtensionDesign design = ExtensionDesign.builder().extensionName("child").build();

        ExtendedEvolvePipeline.recordSkippedDependency(ctx, design, List.of("parent"));

        assertThat(orchestrator.getLastCycleResult().isSuccess()).isFalse();
        assertThat(orchestrator.getLastCycleResult().getSummary()).isEqualTo("skipped extension child");
        assertThat(orchestrator.getLastCycleResult().getError()).isEqualTo("skipped dependency");
    }

    @Test
    @SuppressWarnings("unchecked")
    void topStageResultBuildsPythonStageResultOutput() {
        OutputSchema output = ExtendedEvolvePipeline.topStageResult("build_verify", "running");

        assertThat(output.getType()).isEqualTo("stage_result");
        Map<String, Object> payload = (Map<String, Object>) output.getPayload();
        assertThat(payload).containsEntry("stage", "build_verify").containsEntry("status", "running");
        assertThat(payload.get("messages")).isEqualTo(List.of());
        assertThat(payload.get("metrics")).isEqualTo(Map.of());
    }

    @Test
    void streamRunsAssessPlanDependencyWavesMergeActivateAndStoresResults() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        SessionContext ctx = new SessionContext(orchestrator);
        List<String> buildOrder = new ArrayList<>();
        List<String> activated = new ArrayList<>();
        AtomicBoolean ensuredSkillSources = new AtomicBoolean(false);
        ExtensionDesign constraint = ExtensionDesign.builder()
                .extensionName("guard")
                .kind("constraint")
                .build();
        ExtensionDesign capability = ExtensionDesign.builder()
                .extensionName("writer")
                .kind("capability")
                .dependsOn(List.of("guard"))
                .build();
        ExtensionDesignArtifact designArtifact = ExtensionDesignArtifact.builder()
                .packageName("bundle")
                .designs(List.of(capability, constraint))
                .build();
        ExtendedEvolvePipeline pipeline = new ExtendedEvolvePipeline(
                name -> new ScriptedSessionStage(name, slotFor(name), true, artifactFor(name, designArtifact)),
                (orch, design, verifiedTasks) -> {
                    buildOrder.add(design.getExtensionName());
                    OptimizationTask task = OptimizationTask.builder()
                            .topic("runtime-extension:" + design.getExtensionName())
                            .build();
                    TaskContext taskCtx = new TaskContext(orch, task, new TaskRuntime());
                    taskCtx.putArtifact("runtime_extension", RuntimeExtensionArtifact.builder()
                            .extensionName(design.getExtensionName())
                            .build());
                    verifiedTasks.add(new VerifiedExtensionTask(design, task, taskCtx));
                    return List.of((Object) BaseExecutionContext.message("built " + design.getExtensionName()))
                            .iterator();
                },
                (orch, verified) -> {
                    activated.add(verified.design().getExtensionName());
                    orch.recordCycleResult(CycleResult.builder()
                            .success(true)
                            .summary("activated " + verified.design().getExtensionName())
                            .build());
                    return List.of((Object) BaseExecutionContext.message("activated "
                            + verified.design().getExtensionName())).iterator();
                },
                () -> new MergeActivationBlock() {
                    @Override
                    public Iterator<Object> stream(
                            AutoHarnessOrchestrator orchestrator,
                            List<VerifiedExtensionTask> verifiedTasks,
                            String packageName
                    ) {
                        assertThat(verifiedTasks).extracting(task -> task.design().getExtensionName())
                                .containsExactly("guard", "writer");
                        RuntimeExtensionArtifact artifact = RuntimeExtensionArtifact.builder()
                                .extensionName(packageName)
                                .build();
                        return List.of(
                                MergeActivationBlock.mergeEvent("running", packageName, 0, ""),
                                new MergeSuccessResult(artifact),
                                MergeActivationBlock.mergeEvent("success", packageName, 0, "")
                        ).iterator();
                    }
                },
                config -> ensuredSkillSources.set(true)
        );

        List<Object> events = streamToList(pipeline.stream(ctx));

        assertThat(ensuredSkillSources.get()).isTrue();
        assertThat(buildOrder).containsExactly("guard", "writer");
        assertThat(activated).containsExactly("bundle");
        assertThat(ctx.getArtifact("session_results")).isInstanceOf(SessionResultsArtifact.class);
        SessionResultsArtifact results = (SessionResultsArtifact) ctx.getArtifact("session_results");
        assertThat(results.getResults()).extracting(CycleResult::getSummary).containsExactly("activated bundle");
        assertThat(stagePayloads(events)).contains(
                "build_verify:running",
                "build_verify:success",
                "activate:running",
                "activate:success"
        );
    }

    @Test
    void dependencyWavesSkipFailedAndUnavailableSelectedDependencies() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        SessionContext ctx = new SessionContext(orchestrator);
        ExtensionDesign parent = ExtensionDesign.builder().extensionName("parent").build();
        ExtensionDesign child = ExtensionDesign.builder()
                .extensionName("child")
                .dependsOn(List.of("parent"))
                .build();
        ExtensionDesign missing = ExtensionDesign.builder()
                .extensionName("missing-child")
                .dependsOn(List.of("absent"))
                .build();
        ExtendedEvolvePipeline pipeline = new ExtendedEvolvePipeline(
                null,
                (orch, design, verifiedTasks) -> {
                    if ("parent".equals(design.getExtensionName())) {
                        orch.recordCycleResult(CycleResult.builder()
                                .success(false)
                                .error("parent failed")
                                .build());
                    }
                    return List.of().iterator();
                },
                null,
                null,
                null
        );
        Set<String> failedExtensions = new java.util.LinkedHashSet<>();

        List<Object> events = streamToList(pipeline.runDependencyWaves(
                ctx,
                List.of(parent, child, missing),
                new ArrayList<>(),
                failedExtensions
        ));

        assertThat(failedExtensions).containsExactlyInAnyOrder("parent", "child", "missing-child");
        assertThat(orchestrator.getResults()).hasSize(3);
        assertThat(messageContents(events)).contains(
                "Skipped extension child: failed dependency parent",
                "Skipped extension missing-child: failed dependency absent"
        );
    }

    private static List<Object> streamToList(Iterator<Object> iterator) {
        java.util.ArrayList<Object> result = new java.util.ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .maxTasksPerSession(5)
                .build();
        return new AutoHarnessOrchestrator(config);
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "assess_ext" -> "assess";
            case "plan_ext" -> "plan";
            default -> stageName;
        };
    }

    private static Map<String, Object> artifactFor(
            String stageName,
            ExtensionDesignArtifact designArtifact
    ) {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        if ("plan_ext".equals(stageName)) {
            artifacts.put("extension_design", designArtifact);
        }
        return artifacts;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stagePayloads(List<Object> events) {
        List<String> result = new ArrayList<>();
        for (Object event : events) {
            if (!(event instanceof OutputSchema output) || !"stage_result".equals(output.getType())) {
                continue;
            }
            Map<String, Object> payload = (Map<String, Object>) output.getPayload();
            result.add(String.valueOf(payload.get("stage")) + ":" + payload.get("status"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> messageContents(List<Object> events) {
        List<String> result = new ArrayList<>();
        for (Object event : events) {
            if (!(event instanceof OutputSchema output) || !"message".equals(output.getType())) {
                continue;
            }
            result.add(String.valueOf(((Map<String, Object>) output.getPayload()).get("content")));
        }
        return result;
    }

    private static final class ScriptedSessionStage extends SessionStage {
        private final String name;
        private final String slot;
        private final boolean success;
        private final Map<String, Object> artifacts;

        private ScriptedSessionStage(
                String name,
                String slot,
                boolean success,
                Map<String, Object> artifacts
        ) {
            this.name = name;
            this.slot = slot;
            this.success = success;
            this.artifacts = artifacts;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String slot() {
            return slot;
        }

        @Override
        public Iterator<Object> stream(BaseExecutionContext ctx) {
            StageResult result = StageResult.builder()
                    .status(success ? "success" : "failed")
                    .artifacts(artifacts)
                    .error(success ? "" : name + " failed")
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
