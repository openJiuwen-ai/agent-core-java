/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergedExtensionError;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesignArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock.MergeSuccessResult;
import com.openjiuwen.auto_harness.stages.SessionStage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests/unit_tests/auto_harness/pipelines/test_extended_evolve_merge.py}.
 */
class ExtendedEvolveMergeMissingTest {

    @TempDir
    Path tempDir;

    @Test
    void multiDesignUsesMergeBlock() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        ExtensionDesignArtifact artifact = designArtifact("bundle", design("ext_a"), design("ext_b"));
        AtomicBoolean mergeCalled = new AtomicBoolean(false);
        List<String> activated = new ArrayList<>();
        ExtendedEvolvePipeline pipeline = pipeline(
                artifact,
                () -> new MergeActivationBlock() {
                    @Override
                    public Iterator<Object> stream(
                            AutoHarnessOrchestrator ignored,
                            List<VerifiedExtensionTask> verifiedTasks,
                            String packageName
                    ) {
                        mergeCalled.set(true);
                        assertThat(verifiedTasks).extracting(task -> task.design().getExtensionName())
                                .containsExactly("ext_a", "ext_b");
                        return List.of((Object) new MergeSuccessResult(RuntimeExtensionArtifact.builder()
                                .extensionName(packageName)
                                .build())).iterator();
                    }
                },
                activated
        );

        streamToList(pipeline.stream(new SessionContext(orchestrator)));

        assertThat(mergeCalled.get()).isTrue();
        assertThat(activated).containsExactly("bundle");
    }

    @Test
    void multiDesignMergeFailFast() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        ExtensionDesignArtifact artifact = designArtifact("bundle", design("ext_a"), design("ext_b"));
        List<String> activated = new ArrayList<>();
        ExtendedEvolvePipeline pipeline = pipeline(
                artifact,
                () -> new MergeActivationBlock() {
                    @Override
                    public Iterator<Object> stream(
                            AutoHarnessOrchestrator ignored,
                            List<VerifiedExtensionTask> verifiedTasks,
                            String packageName
                    ) {
                        throw new MergedExtensionError("merge failed");
                    }
                },
                activated
        );

        streamToList(pipeline.stream(new SessionContext(orchestrator)));

        assertThat(activated).isEmpty();
        CycleResult result = orchestrator.getLastCycleResult();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("merge failed");
    }

    @Test
    void singleDesignDoesNotBranchToMerge() {
        List<VerifiedExtensionTask> verified = List.of(verifiedTask(orchestrator(), design("demo_ext")));

        assertThat(verified).hasSize(1);
        assertThat(verified.size() > 1).isFalse();
    }

    @Test
    void multiDesignBranchesToMerge() {
        List<VerifiedExtensionTask> verified = List.of(
                verifiedTask(orchestrator(), design("ext_a")),
                verifiedTask(orchestrator(), design("ext_b"))
        );

        assertThat(verified.size() > 1).isTrue();
        assertThat(verified).hasSize(2);
    }

    private ExtendedEvolvePipeline pipeline(
            ExtensionDesignArtifact artifact,
            ExtendedEvolvePipeline.MergeBlockFactory mergeBlockFactory,
            List<String> activated
    ) {
        return new ExtendedEvolvePipeline(
                name -> new ScriptedSessionStage(name, slotFor(name), Map.of("plan_ext", artifact).get(name)),
                (orchestrator, design, verifiedTasks) -> {
                    verifiedTasks.add(verifiedTask(orchestrator, design));
                    return List.of().iterator();
                },
                (orchestrator, verified) -> {
                    activated.add(verified.design().getExtensionName());
                    orchestrator.recordCycleResult(CycleResult.builder()
                            .success(true)
                            .summary("activated " + verified.design().getExtensionName())
                            .build());
                    return List.of().iterator();
                },
                mergeBlockFactory,
                ignored -> {
                }
        );
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .maxTasksPerSession(5)
                .build();
        return new AutoHarnessOrchestrator(config);
    }

    private static ExtensionDesign design(String name) {
        return ExtensionDesign.builder()
                .gapId("gap_" + name)
                .extensionName(name)
                .build();
    }

    private static ExtensionDesignArtifact designArtifact(String packageName, ExtensionDesign... designs) {
        return ExtensionDesignArtifact.builder()
                .packageName(packageName)
                .designs(List.of(designs))
                .build();
    }

    private static VerifiedExtensionTask verifiedTask(AutoHarnessOrchestrator orchestrator, ExtensionDesign design) {
        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:" + design.getExtensionName())
                .build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        ctx.putArtifact("runtime_extension", RuntimeExtensionArtifact.builder()
                .extensionName(design.getExtensionName())
                .build());
        return new VerifiedExtensionTask(design, task, ctx);
    }

    private static List<Object> streamToList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "assess_ext" -> "assess";
            case "plan_ext" -> "plan";
            default -> stageName;
        };
    }

    private static final class ScriptedSessionStage extends SessionStage {
        private final String name;
        private final String slot;
        private final Object designArtifact;

        private ScriptedSessionStage(String name, String slot, Object designArtifact) {
            this.name = name;
            this.slot = slot;
            this.designArtifact = designArtifact;
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
            Map<String, Object> artifacts = new LinkedHashMap<>();
            if (designArtifact != null) {
                artifacts.put("extension_design", designArtifact);
            }
            StageResult result = StageResult.builder()
                    .status("success")
                    .artifacts(artifacts)
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
