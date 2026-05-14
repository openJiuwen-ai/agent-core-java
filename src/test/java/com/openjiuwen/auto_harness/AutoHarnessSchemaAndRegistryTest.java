package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.auto_harness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.auto_harness.registry.BuiltinRegistries;
import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.registry.StageRegistry;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.Gap;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.ResearchContext;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.StageSpec;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.auto_harness.stages.SessionStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoHarnessSchemaAndRegistryTest {

    @Test
    void schemaDefaultsMirrorPythonIntent() {
        assertEquals("pending", TaskStatus.PENDING.toString());
        assertEquals("optimization", ExperienceType.OPTIMIZATION.toString());

        Gap gap = new Gap();
        assertEquals("", gap.getId());
        assertEquals(0.0, gap.getPriority());
        gap.setImpact(0.8);
        gap.setFeasibility(0.5);
        assertEquals(0.4, gap.getPriority(), 1e-9);

        OptimizationTask task = new OptimizationTask("fix timeout");
        assertEquals("fix timeout", task.getTopic());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertTrue(task.getFiles().isEmpty());

        Experience first = new Experience();
        Experience second = new Experience();
        assertNotEquals(first.getId(), second.getId());
        assertEquals(12, first.getId().length());
        assertEquals(ExperienceType.OPTIMIZATION, first.getType());

        ResearchContext context = new ResearchContext();
        assertTrue(context.getExperiences().isEmpty());
        assertTrue(context.getSourceFiles().isEmpty());

        CycleResult cycleResult = new CycleResult();
        assertEquals("", cycleResult.getSummary());
        assertEquals("", cycleResult.getPrUrl());
        assertEquals(false, cycleResult.isSuccess());

        AutoHarnessConfig config = new AutoHarnessConfig();
        assertEquals("", config.getDataDir());
        assertEquals("", config.getLocalRepo());
        assertEquals(3600.0, config.getSessionBudgetSecs());
        assertEquals(300.0, config.getModelTimeoutSecs());
        assertEquals(3, config.getMaxTasksPerSession());
        assertEquals("", config.getGitRemote());
        assertEquals("", config.getForkOwner());
        assertEquals("", config.getGitUserName());
        assertEquals("", config.getGitcodeUsername());
        assertEquals("GITCODE_ACCESS_TOKEN", config.getGitcodeTokenEnv());
        assertEquals("", config.getCiGatePythonExecutable());
        assertEquals("", config.getCiGateInstallCommand());
        assertTrue(config.getImmutableFiles().isEmpty());
        assertTrue(config.resolveImmutableFiles().size() >= 1);
    }

    @Test
    void configDerivedPathsMirrorPythonIntent() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir("/tmp/ah");
        assertEquals("/tmp/ah/experience", config.getResolvedExperienceDir().replace('\\', '/'));
        assertEquals("/tmp/ah/worktrees", config.getWorktreesDir().replace('\\', '/'));
        assertEquals("/tmp/ah/runs", config.getRunsDir().replace('\\', '/'));
        assertEquals("/tmp/ah/repo/agent-core", config.getCacheRepoDir().replace('\\', '/'));
    }

    @Test
    void registriesSupportRegisterRequireAndDuplicateProtection() {
        StageRegistry stageRegistry = new StageRegistry();
        StageSpec stageSpec = new StageSpec("custom_stage", DummyStage.class, "session", List.of(), List.of("artifact"), "");
        stageRegistry.register(stageSpec);
        assertEquals(DummyStage.class, stageRegistry.require("custom_stage").getStageClass());
        assertThrows(IllegalArgumentException.class, () -> stageRegistry.register(stageSpec));

        PipelineRegistry pipelineRegistry = new PipelineRegistry();
        PipelineSpec pipelineSpec = new PipelineSpec("custom_pipeline", DummyPipeline.class, "", List.of("artifact"));
        pipelineRegistry.register(pipelineSpec);
        assertEquals(DummyPipeline.class, pipelineRegistry.require("custom_pipeline").getPipelineClass());
        assertThrows(IllegalArgumentException.class, () -> pipelineRegistry.require("missing_pipeline"));
    }

    @Test
    void baseStageAndPipelineProduceSpecs() {
        DummyStage stage = new DummyStage();
        assertEquals("custom_stage", stage.spec().getName());
        assertEquals(DummyStage.class, stage.spec().getStageClass());
        assertEquals("session", stage.spec().getScope());

        DummyPipeline pipeline = new DummyPipeline();
        assertEquals("custom_pipeline", pipeline.spec().getName());
        assertEquals(DummyPipeline.class, pipeline.spec().getPipelineClass());
        assertEquals(List.of("custom_artifact"), pipeline.spec().getExpectedOutputs());
    }

    @Test
    void builtinRegistriesMirrorPythonPipelineBuilderIntent() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        StageRegistry stageRegistry = BuiltinRegistries.buildStageRegistry(config);
        assertEquals(com.openjiuwen.auto_harness.stages.AssessStage.class, stageRegistry.require("assess").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.PlanStage.class, stageRegistry.require("plan").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.ImplementStage.class, stageRegistry.require("implement").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.VerifyStage.class, stageRegistry.require("verify").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.CommitStage.class, stageRegistry.require("commit").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.PublishPrStage.class, stageRegistry.require("publish_pr").getStageClass());
        assertEquals(com.openjiuwen.auto_harness.stages.LearningsStage.class, stageRegistry.require("learnings").getStageClass());

        PipelineRegistry pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(config, stageRegistry);
        assertEquals(MetaEvolvePipeline.class, pipelineRegistry.require(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE).getPipelineClass());
        assertEquals(List.of("session_results"), pipelineRegistry.require(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE).getExpectedOutputs());
        assertEquals(ExtendedEvolvePipeline.class, pipelineRegistry.require(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE).getPipelineClass());
    }

    @Test
    void builtinRegistriesLoadCustomRegistrars() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setStageRegistrars(List.of("com.openjiuwen.auto_harness.AutoHarnessSchemaAndRegistryTest:registerTestStage"));
        config.setPipelineRegistrars(List.of("com.openjiuwen.auto_harness.AutoHarnessSchemaAndRegistryTest:registerTestPipeline"));

        StageRegistry stageRegistry = BuiltinRegistries.buildStageRegistry(config);
        assertEquals(DummyStage.class, stageRegistry.require("custom_stage").getStageClass());

        PipelineRegistry pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(config, stageRegistry);
        assertEquals(DummyPipeline.class, pipelineRegistry.require("custom_pipeline").getPipelineClass());
    }

    public static void registerTestStage(StageRegistry registry) {
        registry.register(new StageSpec("custom_stage", DummyStage.class, "session", List.of(), List.of("custom_artifact"), ""));
    }

    public static void registerTestPipeline(PipelineRegistry registry, StageRegistry stageRegistry) {
        stageRegistry.require("custom_stage");
        registry.register(new PipelineSpec("custom_pipeline", DummyPipeline.class, "", List.of("custom_artifact")));
    }

    static class DummyStage extends SessionStage {
        @Override
        public String name() {
            return "custom_stage";
        }

        @Override
        public List<String> produces() {
            return List.of("custom_artifact");
        }

        @Override
        public StageResult run(Object context) {
            return new StageResult();
        }
    }

    static class DummyPipeline extends BasePipeline {
        @Override
        public String name() {
            return "custom_pipeline";
        }

        @Override
        public List<String> expectedOutputs() {
            return List.of("custom_artifact");
        }
    }
}
