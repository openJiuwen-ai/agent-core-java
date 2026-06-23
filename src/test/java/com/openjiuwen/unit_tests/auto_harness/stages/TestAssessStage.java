/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.PipelineStageMap;
import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.ExtensionTaskPipeline;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.PRTaskPipeline;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AssessmentArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.CommitStage;
import com.openjiuwen.auto_harness.stages.ExtendActivateStage;
import com.openjiuwen.auto_harness.stages.ExtendAssessStage;
import com.openjiuwen.auto_harness.stages.ExtendImplementStage;
import com.openjiuwen.auto_harness.stages.ExtendPlanStage;
import com.openjiuwen.auto_harness.stages.ExtendVerifyStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.auto_harness.stages.MetaAssessStage;
import com.openjiuwen.auto_harness.stages.MetaImplementStage;
import com.openjiuwen.auto_harness.stages.MetaPlanStage;
import com.openjiuwen.auto_harness.stages.MetaVerifyStage;
import com.openjiuwen.auto_harness.stages.PlanStage;
import com.openjiuwen.auto_harness.stages.PublishPRStage;
import com.openjiuwen.auto_harness.stages.VerifyStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void fallbackReturnsReport(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));

        String report = AssessStage.runAssessWithFallback(
                config,
                store,
                (ignoredConfig, ignoredRails) -> ignoredInputs -> {
                    throw new IllegalStateException("no model");
                }
        );

        assertTrue(report.contains("评估报告"));
        assertTrue(report.length() > 50);
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
    void assessWithAgentReturnsReport(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));
        String longReport = "# 评估报告\n## 构建状态\nOK\n".repeat(10);

        String report = AssessStage.runAssessWithFallback(
                config,
                store,
                (ignoredConfig, ignoredRails) -> ignoredInputs -> List.of(
                        new OutputSchema("message", 0, Map.of("content", longReport))
                ).iterator()
        );

        assertTrue(report.contains("评估报告"));
        assertEquals(longReport, report);
    }

    @Test
    void shortReportTriggersFallback(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));

        String report = AssessStage.runAssessWithFallback(
                config,
                store,
                (ignoredConfig, ignoredRails) -> ignoredInputs -> List.of(
                        new OutputSchema("message", 0, Map.of("content", "too short"))
                ).iterator()
        );

        assertTrue(report.contains("评估报告"));
        assertTrue(!report.contains("too short"));
    }

    @Test
    void assessStreamYieldsChunks(@TempDir Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));
        List<OutputSchema> chunks = List.of(
                new OutputSchema("llm_output", 0, Map.of("content", "part1")),
                new OutputSchema("llm_output", 1, Map.of("content", "part2"))
        );

        List<Object> collected = collect(AssessStage.runAssessStream(
                config,
                store,
                List.of(),
                List.of(),
                (ignoredConfig, ignoredRails) -> ignoredInputs -> chunks.iterator()
        ));

        assertEquals(2, collected.size());
        OutputSchema first = assertInstanceOf(OutputSchema.class, collected.getFirst());
        Map<?, ?> firstPayload = assertInstanceOf(Map.class, first.getPayload());
        assertEquals("part1", firstPayload.get("content"));
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

    @Test
    void assessBaseStageSlotMatchesPythonStageSlot() {
        assertEquals("assess", new BareAssessStage().slot());
    }

    @Test
    void metaAssessStageSlotMatchesPythonStageSlot() {
        assertEquals("assess", new MetaAssessStage().slot());
    }

    @Test
    void extendAssessStageSlotMatchesPythonStageSlot() {
        assertEquals("assess", new ExtendAssessStage().slot());
    }

    @Test
    void planBaseStageSlotMatchesPythonStageSlot() {
        assertEquals("plan", new BarePlanStage().slot());
    }

    @Test
    void metaPlanStageSlotMatchesPythonStageSlot() {
        assertEquals("plan", new MetaPlanStage().slot());
    }

    @Test
    void extendPlanStageSlotMatchesPythonStageSlot() {
        assertEquals("plan", new ExtendPlanStage().slot());
    }

    @Test
    void implementBaseStageSlotMatchesPythonStageSlot() {
        assertEquals("implement", new BareImplementStage().slot());
    }

    @Test
    void metaImplementStageSlotMatchesPythonStageSlot() {
        assertEquals("implement", new MetaImplementStage().slot());
    }

    @Test
    void extendImplementStageSlotMatchesPythonStageSlot() {
        assertEquals("implement", new ExtendImplementStage().slot());
    }

    @Test
    void verifyBaseStageSlotMatchesPythonStageSlot() {
        assertEquals("verify", new BareVerifyStage().slot());
    }

    @Test
    void metaVerifyStageSlotMatchesPythonStageSlot() {
        assertEquals("verify", new MetaVerifyStage().slot());
    }

    @Test
    void extendVerifyStageSlotMatchesPythonStageSlot() {
        assertEquals("verify", new ExtendVerifyStage().slot());
    }

    @Test
    void commitStageSlotMatchesPythonStageSlot() {
        assertEquals("commit", new CommitStage().slot());
    }

    @Test
    void publishPrStageSlotMatchesPythonStageSlot() {
        assertEquals("publish", new PublishPRStage().slot());
    }

    @Test
    void learningsStageSlotMatchesPythonStageSlot() {
        assertEquals("learnings", new LearningsStage().slot());
    }

    @Test
    void assessFamilyStageNamesStayDistinct() {
        assertEquals("assess", new MetaAssessStage().name());
        assertEquals("assess_ext", new ExtendAssessStage().name());
    }

    @Test
    void planFamilyStageNamesStayDistinct() {
        assertEquals("plan", new MetaPlanStage().name());
        assertEquals("plan_ext", new ExtendPlanStage().name());
    }

    @Test
    void implementFamilyStageNamesStayDistinct() {
        assertEquals("implement", new MetaImplementStage().name());
        assertEquals("implement_ext", new ExtendImplementStage().name());
    }

    @Test
    void verifyFamilyStageNamesStayDistinct() {
        assertEquals("verify", new MetaVerifyStage().name());
        assertEquals("verify_ext", new ExtendVerifyStage().name());
    }

    @Test
    void pipelineStageMapResolveReturnsBoundStageInstance() {
        PipelineStageMap stageMap = new PipelineStageMap(Map.of(StageSlot.COMMIT.value(), CommitStage.class));

        BaseStage stage = stageMap.resolve(StageSlot.COMMIT.value());

        assertInstanceOf(CommitStage.class, stage);
    }

    @Test
    void pipelineStageMapResolveUnknownSlotRaises() {
        PipelineStageMap stageMap = new PipelineStageMap(Map.of());

        NoSuchElementException error = assertThrows(
                NoSuchElementException.class,
                () -> stageMap.resolve("nonexistent")
        );
        assertTrue(error.getMessage().contains("No stage bound"));
    }

    @Test
    void metaEvolvePipelineStageMapMatchesPythonBindings() {
        Map<String, Class<? extends BaseStage>> mapping = new MetaEvolvePipeline().stageMap().getMapping();

        assertSame(MetaAssessStage.class, mapping.get(StageSlot.ASSESS.value()));
        assertSame(MetaPlanStage.class, mapping.get(StageSlot.PLAN.value()));
        assertSame(LearningsStage.class, mapping.get(StageSlot.LEARNINGS.value()));
    }

    @Test
    void extendedEvolvePipelineStageMapMatchesPythonBindings() {
        Map<String, Class<? extends BaseStage>> mapping = new ExtendedEvolvePipeline().stageMap().getMapping();

        assertSame(ExtendAssessStage.class, mapping.get(StageSlot.ASSESS.value()));
        assertSame(ExtendPlanStage.class, mapping.get(StageSlot.PLAN.value()));
    }

    @Test
    void prTaskPipelineStageMapMatchesPythonBindings() {
        Map<String, Class<? extends BaseStage>> mapping = new PRTaskPipeline().stageMap().getMapping();

        assertSame(MetaImplementStage.class, mapping.get(StageSlot.IMPLEMENT.value()));
        assertSame(MetaVerifyStage.class, mapping.get(StageSlot.VERIFY.value()));
        assertSame(CommitStage.class, mapping.get(StageSlot.COMMIT.value()));
        assertSame(PublishPRStage.class, mapping.get(StageSlot.PUBLISH.value()));
    }

    @Test
    void extensionTaskPipelineStageMapMatchesPythonBindings() {
        Map<String, Class<? extends BaseStage>> mapping = new ExtensionTaskPipeline().stageMap().getMapping();

        assertSame(ExtendImplementStage.class, mapping.get(StageSlot.IMPLEMENT.value()));
        assertSame(ExtendVerifyStage.class, mapping.get(StageSlot.VERIFY.value()));
        assertSame(ExtendActivateStage.class, mapping.get(StageSlot.ACTIVATE.value()));
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

    private static final class BareAssessStage extends AssessStage {
    }

    private static final class BarePlanStage extends PlanStage {
        @Override
        public Iterator<Object> stream(com.openjiuwen.auto_harness.contexts.BaseExecutionContext ctx) {
            return List.of().iterator();
        }
    }

    private static final class BareImplementStage extends ImplementStage {
    }

    private static final class BareVerifyStage extends VerifyStage {
        @Override
        public Iterator<Object> stream(com.openjiuwen.auto_harness.contexts.BaseExecutionContext ctx) {
            return List.of().iterator();
        }
    }
}
