/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AssessmentArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesignArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskPlanArtifact;
import com.openjiuwen.auto_harness.stages.DesignExtStage;
import com.openjiuwen.auto_harness.stages.ExtendPlanStage;
import com.openjiuwen.auto_harness.stages.MetaPlanStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan stage parity tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.plan} in
 * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
 */
class TestPlanStage {

    @TempDir
    private Path tempDir;

    @Test
    void metadataMatchesPythonClasses() {
        MetaPlanStage meta = new MetaPlanStage();
        ExtendPlanStage ext = new ExtendPlanStage();

        assertThat(meta.name()).isEqualTo("plan");
        assertThat(meta.slot()).isEqualTo("plan");
        assertThat(meta.consumes()).containsExactly("assessment");
        assertThat(meta.produces()).containsExactly("task_plan");
        assertThat(ext.name()).isEqualTo("plan_ext");
        assertThat(ext.slot()).isEqualTo("plan");
        assertThat(ext.consumes()).containsExactly("gap_analysis");
        assertThat(ext.produces()).containsExactly("extension_design");
        assertThat(new DesignExtStage().name()).isEqualTo("plan_ext");
    }

    @Test
    void planQueryIncludesInputTasksEditScopeAndExperiences() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .optimizationGoal("optimize reporting")
                .maxTasksPerSession(3)
                .selfDrivenSlots(2)
                .build();
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));
        store.record(Experience.builder()
                .type(ExperienceType.INSIGHT)
                .topic("reporting")
                .summary("prefer one task")
                .build()).join();
        OptimizationTask task = OptimizationTask.builder()
                .topic("report_generator")
                .description("generate reports")
                .files(List.of("openjiuwen/harness/report.py"))
                .build();

        String query = MetaPlanStage.buildPlanQuery(config, "assessment body", store, List.of(task));

        assertThat(query).contains("optimize reporting");
        assertThat(query).contains("report_generator");
        assertThat(query).contains("openjiuwen/harness/report.py");
        assertThat(query).contains("assessment body");
        assertThat(query).contains("prefer one task");
        assertThat(query).contains("规划阶段实际输出上限: 1");
        assertThat(query).contains("`openjiuwen/harness/**`");
    }

    @Test
    void metaPlanStreamsAgentOutputCapsOneTaskAndPersistsRawPlan() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        orchestrator.getArtifacts().put("assessment", AssessmentArtifact.builder()
                .report("assess report")
                .build());
        SessionContext ctx = new SessionContext(orchestrator);
        AtomicReference<Map<String, Object>> seenInputs = new AtomicReference<>();
        String planJson = "["
                + "{\"topic\":\"first\",\"description\":\"first task\",\"files\":[\"openjiuwen/harness/a.py\"]},"
                + "{\"topic\":\"second\",\"description\":\"second task\",\"files\":[\"openjiuwen/harness/b.py\"]}"
                + "]";
        MetaPlanStage stage = new MetaPlanStage((ignoredConfig, ignoredRails) -> inputs -> {
            seenInputs.set(inputs);
            return List.of(new OutputSchema("message", 0, Map.of("content", planJson))).iterator();
        });

        StageResult result = lastStageResult(toList(stage.stream(ctx)));
        TaskPlanArtifact artifact = (TaskPlanArtifact) result.getArtifacts().get("task_plan");

        assertThat(artifact.getTasks()).hasSize(1);
        assertThat(artifact.getTasks().get(0).getTopic()).isEqualTo("first");
        assertThat(artifact.getRawPlan()).isEqualTo(planJson);
        assertThat(result.getMessages()).contains("规划阶段只保留最高优先级的 1 个任务");
        assertThat(String.valueOf(seenInputs.get().get("query"))).contains("assess report");
        assertThat(Files.readString(tempDir.resolve("data").resolve("runs").resolve("latest_plan.md")))
                .isEqualTo(planJson);
    }

    @Test
    void metaPlanFallsBackToInputTaskWhenAgentReturnsNoTasks() {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        OptimizationTask task = OptimizationTask.builder().topic("input_task").build();
        orchestrator.getArtifacts().put("input_tasks", List.of(task));
        SessionContext ctx = new SessionContext(orchestrator);
        MetaPlanStage stage = new MetaPlanStage((ignoredConfig, ignoredRails) -> ignoredInputs ->
                List.of(new OutputSchema("message", 0, Map.of("content", "not json"))).iterator());

        StageResult result = lastStageResult(toList(stage.stream(ctx)));
        TaskPlanArtifact artifact = (TaskPlanArtifact) result.getArtifacts().get("task_plan");

        assertThat(artifact.getTasks()).containsExactly(task);
        assertThat(result.getMessages()).contains("规划阶段未生成任务，回退执行最高优先级输入任务");
    }

    @Test
    void extendPlanParsesAgentDesignsCapsConstraintsFirstAndPersists() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .maxTasksPerSession(1)
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        Gap gap = Gap.builder()
                .id("gap_1")
                .feature("ppt_report")
                .gapDescription("generate ppt")
                .impact(0.9)
                .feasibility(0.8)
                .build();
        orchestrator.getArtifacts().put("gap_analysis", GapAnalysisArtifact.builder()
                .gaps(List.of(gap))
                .build());
        SessionContext ctx = new SessionContext(orchestrator);
        AtomicReference<Map<String, Object>> seenInputs = new AtomicReference<>();
        String designJson = "{\"package_name\":\"ppt_report\",\"designs\":["
                + "{\"gap_id\":\"gap_1\",\"extension_name\":\"capability_a\",\"kind\":\"capability\",\"components\":[\"tool\"]},"
                + "{\"gap_id\":\"gap_1\",\"extension_name\":\"constraint_a\",\"kind\":\"constraint\",\"components\":[\"rail\"]}"
                + "]}";
        ExtendPlanStage stage = new ExtendPlanStage((ignoredConfig, ignoredRails) -> inputs -> {
            seenInputs.set(inputs);
            return List.of(new OutputSchema("message", 0, Map.of("content", designJson))).iterator();
        });

        StageResult result = lastStageResult(toList(stage.stream(ctx)));
        ExtensionDesignArtifact artifact = (ExtensionDesignArtifact) result.getArtifacts().get("extension_design");

        assertThat(artifact.getDesigns()).hasSize(1);
        assertThat(artifact.getDesigns().get(0).getExtensionName()).isEqualTo("constraint_a");
        assertThat(artifact.getPackageName()).startsWith("ppt_report_");
        assertThat(String.valueOf(seenInputs.get().get("query"))).contains("ppt_report");
        assertThat(Files.exists(tempDir.resolve("data").resolve("runs").resolve("latest_extension_design.json")))
                .isTrue();
        assertThat(Files.list(tempDir.resolve("data").resolve("runs"))
                .anyMatch(path -> path.getFileName().toString().startsWith("extension_design_")))
                .isTrue();
    }

    @Test
    void fallbackDesignInferenceMatchesPythonRules() {
        Gap constraint = Gap.builder()
                .id("gap_c")
                .feature("文件名硬约束")
                .gapDescription("必须检查文件名后缀")
                .suggestedApproach("写入前校验")
                .impact(0.5)
                .feasibility(0.5)
                .build();
        Gap capability = Gap.builder()
                .id("gap_a")
                .competitor("Cursor")
                .feature("PPT Report")
                .gapDescription("生成报告")
                .suggestedApproach("use template skill")
                .impact(0.9)
                .feasibility(0.9)
                .build();

        List<ExtensionDesign> designs = ExtendPlanStage.buildFallbackDesigns(
                List.of(constraint, capability),
                1
        );

        assertThat(designs).hasSize(2);
        assertThat(designs.get(0).getKind()).isEqualTo("constraint");
        assertThat(designs.get(0).getComponents()).startsWith("rail");
        assertThat(designs.get(1).getExtensionName()).isEqualTo("cursor_ppt_report");
        assertThat(designs.get(1).getComponents()).contains("tool", "skill");
        assertThat(designs.get(1).getFilePlan().get("manifest"))
                .isEqualTo("openjiuwen/extensions/harness/cursor_ppt_report/harness_config.yaml");
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> values) {
        return (StageResult) values.get(values.size() - 1);
    }
}
