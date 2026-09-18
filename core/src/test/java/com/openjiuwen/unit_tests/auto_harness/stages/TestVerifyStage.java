/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.ExtStaticCheckResult;
import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CodeChangeArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.ExtendVerifyStage;
import com.openjiuwen.auto_harness.stages.MetaVerifyStage;
import com.openjiuwen.auto_harness.stages.VerifyExtStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify stage parity tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.verify} in
 * {@code openjiuwen/auto_harness/stages/verify.py}.</p>
 */
class TestVerifyStage {

    @TempDir
    private Path tempDir;

    @Test
    void metadataMatchesPythonClasses() {
        MetaVerifyStage meta = new MetaVerifyStage();
        ExtendVerifyStage ext = new ExtendVerifyStage();

        assertThat(meta.name()).isEqualTo("verify");
        assertThat(meta.slot()).isEqualTo("verify");
        assertThat(meta.consumes()).containsExactly("code_change");
        assertThat(meta.produces()).containsExactly("verify_report");
        assertThat(ext.name()).isEqualTo("verify_ext");
        assertThat(ext.slot()).isEqualTo("verify");
        assertThat(ext.consumes()).containsExactly("extension_build");
        assertThat(ext.produces()).containsExactly("extension_build", "verify_report");
        assertThat(new VerifyExtStage().name()).isEqualTo("verify_ext");
    }

    @Test
    void ciMessageHelpersSummarizePassAndFailure() {
        Map<String, Object> ci = Map.of(
                "passed", false,
                "gates", List.of(
                        Map.of("name", "compile", "passed", true, "output", "ok"),
                        Map.of("name", "test", "passed", false, "output", "line1\nline2")
                )
        );

        assertThat(VerifyStage.iterCiGateMessages(ci)).contains(
                "CI 结果: compile=PASS, test=FAIL",
                "[test] line1\nline2"
        );
        assertThat(VerifyStage.formatCiStatusForEvaluator(ci))
                .contains("结论: blocking failure")
                .contains("- test: FAIL | line1\nline2");
    }

    @Test
    void metaVerifySuccessYieldsVerifyReport() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        orchestrator.setCiGate(new FakeCIGateRunner(Map.of(
                "passed", true,
                "gates", List.of(Map.of("name", "compile", "passed", true, "output", "ok"))
        )));
        TaskContext ctx = new TaskContext(
                orchestrator,
                OptimizationTask.builder().topic("verify").build(),
                new TaskRuntime()
        );
        ctx.putArtifact("code_change", CodeChangeArtifact.builder().build());

        StageResult result = lastStageResult(toList(new MetaVerifyStage().stream(ctx)));

        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        assertThat(report.getCiResult()).containsEntry("passed", true);
        assertThat(result.getMessages()).contains("CI 结果: compile=PASS");
    }

    @Test
    void metaVerifyFailedFixLoopRevertsTask() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        orchestrator.setCiGate(new FakeCIGateRunner(Map.of(
                "passed", false,
                "gates", List.of(Map.of("name", "test", "passed", false, "output", "boom")),
                "errors", "boom"
        )));
        orchestrator.setFixLoop(new FixLoopController(1, 0, 1.0));
        RecordingGitOperations git = new RecordingGitOperations();
        orchestrator.setGit(git);
        OptimizationTask task = OptimizationTask.builder().topic("verify").build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());

        StageResult result = lastStageResult(toList(new MetaVerifyStage().stream(ctx)));

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.REVERTED);
        assertThat(git.discarded).isTrue();
        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        assertThat(report.isReverted()).isTrue();
    }

    @Test
    void extendVerifyStaticFailureYieldsFailedArtifacts() {
        TaskContext ctx = extensionContext();
        ExtStaticCheckResult staticResult = new ExtStaticCheckResult(List.of("manifest invalid"), 1, 2, 3, 1);
        ExtendVerifyStage stage = new ExtendVerifyStage(
                ignored -> new ExtendVerifyStage.InstallResult(true, ""),
                (runtime, prefix) -> staticResult,
                (taskContext, build, rails, tools, skills) -> new ExtendVerifyStage.AcceptanceRun(
                        List.of(),
                        new ExtendVerifyStage.CIResult(true, "")
                )
        );

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("manifest invalid");
        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        assertThat(report.getCiResult()).containsEntry("rails", 1).containsEntry("tools", 2);
    }

    @Test
    void extendVerifyPromotesRuntimeOnStaticAndAcceptanceSuccess() throws Exception {
        TaskContext ctx = extensionContext();
        List<Object> acceptanceEvents = List.of(ctx.message("acceptance ok"));
        ExtendVerifyStage stage = new ExtendVerifyStage(
                ignored -> new ExtendVerifyStage.InstallResult(true, ""),
                (runtime, prefix) -> new ExtStaticCheckResult(List.of(), 1, 2, 3, 1),
                (taskContext, build, rails, tools, skills) -> new ExtendVerifyStage.AcceptanceRun(
                        acceptanceEvents,
                        new ExtendVerifyStage.CIResult(true, "")
                )
        );

        List<Object> events = toList(stage.stream(ctx));
        StageResult result = lastStageResult(events);

        assertThat(events.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .map(OutputSchema::getPayload)
                .map(String::valueOf)
                .toList()).anySatisfy(payload -> assertThat(payload).contains("acceptance ok"));
        RuntimeExtensionArtifact runtime = (RuntimeExtensionArtifact) result.getArtifacts().get("runtime_extension");
        assertThat(runtime.getExtensionName()).isEqualTo("demo_ext");
        assertThat(Files.exists(Path.of(runtime.getRuntimePath()).resolve("harness_config.yaml"))).isTrue();
        VerifyReportArtifact report = (VerifyReportArtifact) result.getArtifacts().get("verify_report");
        assertThat(report.getCiResult()).containsEntry("passed", true).containsEntry("skills", 3);
    }

    @Test
    void failureIdsAreGroupedByLayer() {
        String output = "failure_id=artifact_not_created: x\nfailure_id=module_import_failed: y";

        assertThat(ExtendVerifyStage.extractFailureIdsFromPytestOutput(output))
                .contains("L1 结构类")
                .contains("module_import_failed")
                .contains("L3 运行时类")
                .contains("artifact_not_created");
    }

    private AutoHarnessOrchestrator orchestrator() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace(tempDir.toString());
        return new AutoHarnessOrchestrator(config);
    }

    private TaskContext extensionContext() {
        AutoHarnessOrchestrator orchestrator = orchestrator();
        Path extRoot = tempDir.resolve("extension-root");
        try {
            Files.createDirectories(extRoot);
            Files.writeString(extRoot.resolve("harness_config.yaml"), "schema_version: harness_config.v0.1\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        TaskContext ctx = new TaskContext(
                orchestrator,
                OptimizationTask.builder().topic("verify_ext").build(),
                new TaskRuntime()
        );
        ctx.putArtifact("extension_build", ExtensionBuildArtifact.builder()
                .extensionName("demo_ext")
                .extensionRoot(extRoot.toString())
                .configPath(extRoot.resolve("harness_config.yaml").toString())
                .build());
        return ctx;
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> events) {
        return (StageResult) events.get(events.size() - 1);
    }

    private static final class FakeCIGateRunner extends CIGateRunner {
        private final Map<String, Object> result;

        private FakeCIGateRunner(Map<String, Object> result) {
            super("");
            this.result = result;
        }

        @Override
        public CompletableFuture<Map<String, Object>> run(String action) {
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class RecordingGitOperations extends GitOperations {
        private boolean discarded;

        private RecordingGitOperations() {
            super("");
        }

        @Override
        public boolean discardWorktreeChanges() {
            discarded = true;
            return true;
        }
    }
}
