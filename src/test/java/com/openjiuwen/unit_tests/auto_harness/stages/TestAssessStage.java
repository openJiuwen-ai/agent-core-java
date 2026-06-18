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
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.auto_harness.stages.ExtendAssessStage;
import com.openjiuwen.auto_harness.stages.MetaAssessStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assess stage parity tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.assess} in
 * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
 *
 * <p>Mirrors Python's {@code test_assessor} and stage slot assertions in
 * {@code tests/unit_tests/auto_harness/stages/test_assessor.py} and
 * {@code tests/unit_tests/auto_harness/stages/test_stage_slot.py}.</p>
 */
class TestAssessStage {

    @Test
    void formatStrategyPrefersStagedMakeTargets() {
        String strategy = AssessStage.formatPythonCheckStrategy(
                List.of("openjiuwen/auto_harness/agent.py"),
                List.of(),
                List.of()
        );

        assertTrue(strategy.contains("`make check`"));
        assertTrue(strategy.contains("`make type-check`"));
        assertTrue(strategy.contains("staged"));
    }

    @Test
    void formatStrategyUsesExplicitToolsForWorktreeDelta() {
        String strategy = AssessStage.formatPythonCheckStrategy(
                List.of(),
                List.of("openjiuwen/auto_harness/agent.py"),
                List.of("tests/unit_tests/auto_harness/test_agent.py")
        );

        assertTrue(strategy.contains("不要运行 `make check COMMITS=1`"));
        assertTrue(strategy.contains("`uv run ruff check <files>`"));
        assertTrue(strategy.contains("`uv run mypy <files>`"));
    }

    @Test
    void formatStrategyMarksEmptySnapshotAsNotApplicable() {
        String strategy = AssessStage.formatPythonCheckStrategy(List.of(), List.of(), List.of());

        assertTrue(strategy.contains("No Python files selected"));
        assertTrue(strategy.contains("未执行"));
    }

    @Test
    void buildQueryIncludesPythonCheckStrategyAndEditScope(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));

        String query = AssessStage.buildQuery(config, store, List.of());

        assertTrue(query.contains("Python 检查策略建议"));
        assertTrue(query.contains("`openjiuwen/harness/**`"));
        assertTrue(query.contains("`openjiuwen/core/**`"));
        assertTrue(query.contains("`openjiuwen/harness/cli/README.md`"));
        assertTrue(query.contains("`tests/**`"));
        assertTrue(query.contains("`examples/**`"));
        assertTrue(query.contains("`docs/en/`"));
        assertTrue(query.contains("`docs/zh/`"));
        assertTrue(query.contains("`openjiuwen/auto_harness/**`"));
    }

    @Test
    void fallbackIncludesExperienceRecords(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));
        store.record(Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("lint-fix")
                .summary("ruff failed")
                .build()).join();

        String report = AssessStage.runAssessWithFallback(
                config,
                store,
                (ignoredConfig, ignoredRails) -> ignoredInputs -> {
                    throw new IllegalStateException("no model");
                }
        );

        assertTrue(report.contains("评估报告"));
        assertTrue(report.contains("lint-fix"));
    }

    @Test
    void metaAssessUsesInputTasksAsAgentFocus(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, null);
        OptimizationTask task = OptimizationTask.builder().topic("生成预算报告扩展").build();
        orchestrator.getArtifacts().put("input_tasks", List.of(task));
        SessionContext ctx = new SessionContext(orchestrator);
        AtomicReference<Map<String, Object>> seenInputs = new AtomicReference<>();
        MetaAssessStage stage = new MetaAssessStage((ignoredConfig, ignoredRails) -> inputs -> {
            seenInputs.set(inputs);
            return List.of(new OutputSchema("message", 0, Map.of("content", "# assessment"))).iterator();
        });

        List<Object> results = collect(stage.stream(ctx));
        StageResult result = lastStageResult(results);
        AssessmentArtifact artifact = assertInstanceOf(
                AssessmentArtifact.class,
                result.getArtifacts().get("assessment")
        );

        assertEquals("# assessment", artifact.getReport());
        assertTrue(String.valueOf(seenInputs.get().get("query")).contains("生成预算报告扩展"));
    }

    @Test
    void extendAssessUsesInputTasksAsAgentFocus(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, null);
        OptimizationTask task = OptimizationTask.builder()
                .topic("conversation_budget_report")
                .description("budget report")
                .build();
        orchestrator.getArtifacts().put("input_tasks", List.of(task));
        SessionContext ctx = new SessionContext(orchestrator);
        AtomicReference<Map<String, Object>> seenInputs = new AtomicReference<>();
        ExtendAssessStage stage = new ExtendAssessStage((ignoredConfig, ignoredRails) -> inputs -> {
            seenInputs.set(inputs);
            String table = "| 竞品 | 功能 | 当前状态 | 差距描述 | 影响(0-1) | 可行性(0-1) | 建议方案 | 目标文件 |\n"
                    + "| --- | --- | --- | --- | --- | --- | --- | --- |\n"
                    + "| cursor | conversation_budget_report | missing | no report | 0.9 | 0.8 | add report | openjiuwen/extensions/harness/report |\n";
            return List.of(new OutputSchema("message", 0, Map.of("content", table))).iterator();
        });

        StageResult result = lastStageResult(collect(stage.stream(ctx)));
        GapAnalysisArtifact artifact = assertInstanceOf(
                GapAnalysisArtifact.class,
                result.getArtifacts().get("gap_analysis")
        );

        assertEquals("conversation_budget_report", artifact.getGaps().get(0).getFeature());
        assertTrue(String.valueOf(seenInputs.get().get("query")).contains("conversation_budget_report"));
    }

    @Test
    void extendAssessQueryMarksRuntimeExtensionMode() {
        String query = AssessStage.buildGapQuery(
                List.of(OptimizationTask.builder()
                        .topic("huawei_ppt_generator")
                        .description("帮我优化创建一个能生成华为风格ppt的办公拓展")
                        .build()),
                ""
        );

        assertTrue(query.contains("评估模式: runtime_extension_gap_assessment"));
        assertTrue(query.contains("当前 pipeline: extended_evolve_pipeline"));
        assertTrue(query.contains("华为风格ppt"));
        assertTrue(!query.contains("主流编码 agent 的能力差距"));
        assertTrue(query.contains("只有用户明确要求"));
    }

    @Test
    void stageMetadataMatchesPythonClasses() {
        assertEquals("assess", new MetaAssessStage().name());
        assertEquals("assess_ext", new ExtendAssessStage().name());
        assertEquals("assess", new MetaAssessStage().slot());
        assertEquals("assess", new ExtendAssessStage().slot());
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static StageResult lastStageResult(List<Object> values) {
        Object last = values.get(values.size() - 1);
        return assertInstanceOf(StageResult.class, last);
    }
}
