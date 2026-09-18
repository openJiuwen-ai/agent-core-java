/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergedExtensionError;
import com.openjiuwen.auto_harness.infra.SkillSourceManager;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.pipelines.PipelineStageMap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesignArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock;
import com.openjiuwen.auto_harness.stages.MergeActivationBlock.MergeSuccessResult;
import com.openjiuwen.auto_harness.stages.SessionStage;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Session pipeline for isolated extension evolution.
 *
 * <p>Mirrors Python's {@code ExtendedEvolvePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extended_evolve_pipeline.py}.</p>
 */
public class ExtendedEvolvePipeline extends BasePipeline {

    private static final Logger LOGGER = Logger.getLogger(ExtendedEvolvePipeline.class.getName());

    private static final String EXTENSION_DESIGN = "extension_design";
    private static final String SESSION_RESULTS = "session_results";
    private static final String RUNTIME_EXTENSION = "runtime_extension";
    private static final String EXTENSION_TARGET = "extension_target";
    private static final String ASSESS_STAGE_NAME = "assess_ext";
    private static final String PLAN_STAGE_NAME = "plan_ext";
    private static final String ASSESS_STAGE_CLASS = "com.openjiuwen.auto_harness.stages.ExtendAssessStage";
    private static final String PLAN_STAGE_CLASS = "com.openjiuwen.auto_harness.stages.ExtendPlanStage";

    private final StageFactory stageFactory;
    private final BuildVerifyRunner buildVerifyRunner;
    private final ActivateRunner activateRunner;
    private final MergeBlockFactory mergeBlockFactory;
    private final SkillSourceEnsurer skillSourceEnsurer;

    public ExtendedEvolvePipeline() {
        this(
                ExtendedEvolvePipeline::createDefaultStage,
                ExtensionTaskPipeline::runBuildVerifyIsolatedStream,
                ExtensionTaskPipeline::runActivateStream,
                MergeActivationBlock::new,
                SkillSourceManager::ensureSkillSources
        );
    }

    ExtendedEvolvePipeline(
            StageFactory stageFactory,
            BuildVerifyRunner buildVerifyRunner,
            ActivateRunner activateRunner,
            MergeBlockFactory mergeBlockFactory,
            SkillSourceEnsurer skillSourceEnsurer
    ) {
        this.stageFactory = stageFactory == null ? ExtendedEvolvePipeline::createDefaultStage : stageFactory;
        this.buildVerifyRunner = buildVerifyRunner == null
                ? ExtensionTaskPipeline::runBuildVerifyIsolatedStream
                : buildVerifyRunner;
        this.activateRunner = activateRunner == null ? ExtensionTaskPipeline::runActivateStream : activateRunner;
        this.mergeBlockFactory = mergeBlockFactory == null ? MergeActivationBlock::new : mergeBlockFactory;
        this.skillSourceEnsurer = skillSourceEnsurer == null ? SkillSourceManager::ensureSkillSources : skillSourceEnsurer;
    }

    @Override
    public String name() {
        return AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE;
    }

    @Override
    public String description() {
        return "Extended evolve generation pipeline.";
    }

    @Override
    public List<String> expectedOutputs() {
        return List.of("extension_design", "session_results");
    }

    @Override
    public PipelineStageMap stageMap() {
        Map<String, Class<? extends BaseStage>> mapping = new LinkedHashMap<>();
        mapping.put(StageSlot.ASSESS.value(), loadStageClass(ASSESS_STAGE_CLASS));
        mapping.put(StageSlot.PLAN.value(), loadStageClass(PLAN_STAGE_CLASS));
        return new PipelineStageMap(mapping);
    }

    @Override
    public List<StageOrderEntry> stageOrder() {
        return List.of(
                new StageOrderEntry("assess", "评估扩展缺口"),
                new StageOrderEntry("plan", "设计扩展方案"),
                new StageOrderEntry("build_verify", "实现/验证扩展"),
                new StageOrderEntry("activate", "激活扩展")
        );
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("ExtendedEvolvePipeline requires a SessionContext");
        }
        return stream(sessionContext);
    }

    public Iterator<Object> stream(SessionContext ctx) {
        skillSourceEnsurer.ensure(ctx.getOrchestrator().getConfig());
        List<Object> events = new ArrayList<>();
        LOGGER.info("[AutoHarnessExtendedPipeline] session pipeline start: max_tasks="
                + ctx.getOrchestrator().getConfig().getMaxTasksPerSession()
                + " budget_remaining=" + ctx.getOrchestrator().getBudget().getRemainingSecs());

        BaseStage assessStage = stageFactory.create(ASSESS_STAGE_NAME);
        List<StageResult> assessResultHolder = new ArrayList<>();
        streamStage(assessStage, ctx, assessResultHolder).forEachRemaining(events::add);
        if (didStageFail(assessStage, assessResultHolder)) {
            LOGGER.warning("[AutoHarnessExtendedPipeline] assess failed, stop session pipeline");
            return events.iterator();
        }

        BaseStage planStage = stageFactory.create(PLAN_STAGE_NAME);
        List<StageResult> planResultHolder = new ArrayList<>();
        streamStage(planStage, ctx, planResultHolder).forEachRemaining(events::add);
        if (didStageFail(planStage, planResultHolder)) {
            LOGGER.warning("[AutoHarnessExtendedPipeline] plan failed, stop session pipeline");
            return events.iterator();
        }

        Object designArtifact = ctx.requireArtifact(EXTENSION_DESIGN);
        if (!(designArtifact instanceof ExtensionDesignArtifact extensionDesignArtifact)) {
            throw new IllegalStateException("extension_design artifact must be ExtensionDesignArtifact");
        }

        List<ExtensionDesign> designsToRun = selectDesigns(
                extensionDesignArtifact.getDesigns(),
                ctx.getOrchestrator().getConfig().getMaxTasksPerSession()
        );
        LOGGER.info("[AutoHarnessExtendedPipeline] design selection: total="
                + safeDesigns(extensionDesignArtifact.getDesigns()).size()
                + " selected=" + designsToRun.size()
                + " selected_names=" + designsToRun.stream().map(ExtensionDesign::getExtensionName).toList());
        List<VerifiedExtensionTask> verifiedTasks = new ArrayList<>();
        Set<String> failedExtensions = new LinkedHashSet<>();

        events.add(topStageResult("build_verify", "running"));
        runDependencyWaves(ctx, designsToRun, verifiedTasks, failedExtensions).forEachRemaining(events::add);
        events.add(topStageResult("build_verify", failedExtensions.isEmpty() ? "success" : "failed"));

        if (!verifiedTasks.isEmpty()) {
            events.add(topStageResult("activate", "running"));
        }

        int activateStartedAt = ctx.getOrchestrator().getResults().size();
        if (verifiedTasks.size() == 1) {
            LOGGER.info("[AutoHarnessExtendedPipeline] only get 1 verified tasks");
            activateRunner.run(ctx.getOrchestrator(), verifiedTasks.get(0)).forEachRemaining(events::add);
        } else if (verifiedTasks.size() > 1) {
            LOGGER.info("[AutoHarnessExtendedPipeline] get multiple verified tasks");
            activateMergedTasks(
                    ctx.getOrchestrator(),
                    verifiedTasks,
                    extensionDesignArtifact.getPackageName()
            ).forEachRemaining(events::add);
        }

        if (!verifiedTasks.isEmpty()) {
            List<CycleResult> results = ctx.getOrchestrator().getResults();
            boolean activateFailed = results.subList(Math.min(activateStartedAt, results.size()), results.size())
                    .stream()
                    .anyMatch(result -> !result.isSuccess());
            events.add(topStageResult("activate", activateFailed ? "failed" : "success"));
        }

        ctx.putArtifact(SESSION_RESULTS, SessionResultsArtifact.builder()
                .results(ctx.getOrchestrator().getResults())
                .build());
        LOGGER.info("[AutoHarnessExtendedPipeline] session pipeline end: results="
                + ctx.getOrchestrator().getResults().size()
                + " failed_extensions=" + failedExtensions);
        return events.iterator();
    }

    public Iterator<Object> runDependencyWaves(
            SessionContext ctx,
            List<ExtensionDesign> designs,
            List<VerifiedExtensionTask> verifiedTasks,
            Set<String> failedExtensions
    ) {
        Map<String, ExtensionDesign> pending = new LinkedHashMap<>();
        for (ExtensionDesign design : safeDesigns(designs)) {
            pending.put(design.getExtensionName(), design);
        }
        Set<String> selectedNames = new LinkedHashSet<>(pending.keySet());
        Set<String> completed = new LinkedHashSet<>();
        List<Object> events = new ArrayList<>();
        int waveIndex = 0;

        while (!pending.isEmpty()) {
            if (ctx.getOrchestrator().shouldCancel()) {
                LOGGER.info("[AutoHarnessExtendedPipeline] cancellation requested, stopping dependency waves");
                break;
            }

            List<ExtensionDesign> skipped = new ArrayList<>();
            for (ExtensionDesign design : pending.values()) {
                if (hasUnmetSelectedDependency(design, failedExtensions, selectedNames)) {
                    skipped.add(design);
                }
            }
            for (ExtensionDesign design : skipped) {
                List<String> unmet = collectUnmetSelectedDependencies(design, failedExtensions, selectedNames);
                recordSkippedDependency(ctx, design, unmet);
                pending.remove(design.getExtensionName());
                failedExtensions.add(design.getExtensionName());
                events.add(ctx.getOrchestrator().messageOutput(
                        "Skipped extension " + design.getExtensionName()
                                + ": failed dependency " + String.join(", ", unmet)
                ));
            }

            List<ExtensionDesign> ready = new ArrayList<>();
            for (ExtensionDesign design : pending.values()) {
                if (dependenciesCompleted(design, completed)) {
                    ready.add(design);
                }
            }
            if (ready.isEmpty()) {
                for (ExtensionDesign design : new ArrayList<>(pending.values())) {
                    List<String> unmet = collectIncompleteDependencies(design, completed);
                    recordSkippedDependency(ctx, design, unmet);
                    failedExtensions.add(design.getExtensionName());
                    events.add(ctx.getOrchestrator().messageOutput(
                            "Skipped extension " + design.getExtensionName()
                                    + ": unresolved dependency " + String.join(", ", unmet)
                    ));
                }
                pending.clear();
                break;
            }

            waveIndex++;
            for (ExtensionDesign design : ready) {
                pending.remove(design.getExtensionName());
            }
            LOGGER.info("[AutoHarnessExtendedPipeline] extension wave dispatch: wave="
                    + waveIndex + " extensions=" + ready.stream().map(ExtensionDesign::getExtensionName).toList());
            Map<String, Boolean> waveResults = new LinkedHashMap<>();
            runBuildVerifyWave(ctx, ready, verifiedTasks, waveResults).forEachRemaining(events::add);
            for (ExtensionDesign design : ready) {
                if (Boolean.TRUE.equals(waveResults.get(design.getExtensionName()))) {
                    completed.add(design.getExtensionName());
                } else {
                    failedExtensions.add(design.getExtensionName());
                }
            }
        }
        return events.iterator();
    }

    public static Iterator<Object> runDependencyWaves(
            SessionContext ctx,
            List<ExtensionDesign> designs,
            Set<String> failedExtensions
    ) {
        return new ExtendedEvolvePipeline().runDependencyWaves(ctx, designs, new ArrayList<>(), failedExtensions);
    }

    public Iterator<Object> runBuildVerifyWave(
            SessionContext ctx,
            List<ExtensionDesign> designs,
            List<VerifiedExtensionTask> verifiedTasks,
            Map<String, Boolean> waveResults
    ) {
        List<Object> events = new ArrayList<>();
        for (ExtensionDesign design : safeDesigns(designs)) {
            if (ctx.getOrchestrator().shouldCancel()) {
                LOGGER.info("[AutoHarnessExtendedPipeline] cancellation requested, skipping extension "
                        + design.getExtensionName());
                waveResults.put(design.getExtensionName(), false);
                continue;
            }
            if (ctx.getOrchestrator().getBudget().isShouldStop()) {
                waveResults.put(design.getExtensionName(), false);
                continue;
            }
            if (!ctx.getOrchestrator().getBudget().checkTaskBudget(1.0)) {
                waveResults.put(design.getExtensionName(), false);
                continue;
            }

            List<VerifiedExtensionTask> localVerified = new ArrayList<>();
            int resultCount = ctx.getOrchestrator().getResults().size();
            LOGGER.info("[AutoHarnessExtendedPipeline] extension build/verify dispatch: extension="
                    + design.getExtensionName() + " kind=" + design.getKind()
                    + " depends_on=" + design.getDependsOn());
            buildVerifyRunner.run(ctx.getOrchestrator(), design, localVerified).forEachRemaining(events::add);
            if (!localVerified.isEmpty()) {
                verifiedTasks.addAll(localVerified);
                waveResults.put(design.getExtensionName(), true);
            } else if (ctx.getOrchestrator().getResults().size() > resultCount) {
                List<CycleResult> results = ctx.getOrchestrator().getResults();
                waveResults.put(design.getExtensionName(), results.get(results.size() - 1).isSuccess());
            } else {
                waveResults.put(design.getExtensionName(), false);
            }
        }
        return events.iterator();
    }

    public static boolean hasUnmetSelectedDependency(
            ExtensionDesign design,
            Set<String> failedExtensions,
            Set<String> selectedNames
    ) {
        for (String dependency : dependencies(design)) {
            if (failedExtensions != null && failedExtensions.contains(dependency)) {
                return true;
            }
            if (selectedNames == null || !selectedNames.contains(dependency)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> collectUnmetSelectedDependencies(
            ExtensionDesign design,
            Set<String> failedExtensions,
            Set<String> selectedNames
    ) {
        List<String> unmet = new ArrayList<>();
        for (String dependency : dependencies(design)) {
            if ((failedExtensions != null && failedExtensions.contains(dependency))
                    || selectedNames == null
                    || !selectedNames.contains(dependency)) {
                unmet.add(dependency);
            }
        }
        return unmet;
    }

    public static boolean dependenciesCompleted(ExtensionDesign design, Set<String> completed) {
        for (String dependency : dependencies(design)) {
            if (completed == null || !completed.contains(dependency)) {
                return false;
            }
        }
        return true;
    }

    public static List<String> collectIncompleteDependencies(ExtensionDesign design, Set<String> completed) {
        List<String> unmet = new ArrayList<>();
        for (String dependency : dependencies(design)) {
            if (completed == null || !completed.contains(dependency)) {
                unmet.add(dependency);
            }
        }
        return unmet;
    }

    public static void recordSkippedDependency(SessionContext ctx, ExtensionDesign design, List<String> unmet) {
        ctx.getOrchestrator().recordCycleResult(CycleResult.builder()
                .success(false)
                .summary("skipped extension " + design.getExtensionName())
                .error("skipped dependency")
                .errorLog("Skipped because dependency failed or was unavailable: " + String.join(", ", unmet))
                .build());
    }

    public static OutputSchema topStageResult(String stage, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("status", status);
        payload.put("messages", List.of());
        payload.put("metrics", Map.of());
        return new OutputSchema("stage_result", 0, payload);
    }

    private Iterator<Object> activateMergedTasks(
            AutoHarnessOrchestrator orchestrator,
            List<VerifiedExtensionTask> verifiedTasks,
            String packageName
    ) {
        List<Object> events = new ArrayList<>();
        RuntimeExtensionArtifact mergedArtifact = null;
        try {
            Iterator<Object> stream = mergeBlockFactory.create().stream(orchestrator, verifiedTasks, packageName);
            while (stream.hasNext()) {
                Object chunk = stream.next();
                if (chunk instanceof MergeSuccessResult success) {
                    mergedArtifact = success.artifact();
                } else {
                    events.add(chunk);
                }
            }
        } catch (MergedExtensionError exception) {
            orchestrator.recordCycleResult(CycleResult.builder()
                    .success(false)
                    .error("merge multiple extensions failed: " + exception.getMessage())
                    .build());
            return events.iterator();
        }

        if (mergedArtifact == null) {
            orchestrator.recordCycleResult(CycleResult.builder()
                    .success(false)
                    .error("merge multiple extensions failed: missing merged artifact")
                    .build());
            return events.iterator();
        }

        VerifiedExtensionTask mergedVerified = buildMergedVerifiedTask(orchestrator, mergedArtifact);
        activateRunner.run(orchestrator, mergedVerified).forEachRemaining(events::add);
        return events.iterator();
    }

    public static VerifiedExtensionTask buildMergedVerifiedTask(
            AutoHarnessOrchestrator orchestrator,
            RuntimeExtensionArtifact merged
    ) {
        ExtensionDesign design = ExtensionDesign.builder()
                .gapId("merged")
                .extensionName(merged == null ? "" : merged.getExtensionName())
                .kind("merged")
                .build();
        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:" + design.getExtensionName())
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(List.of());
        runtime.setWtPath(orchestrator.ensureSessionRuntimeDir().toString());
        runtime.setEditSafetyRail(null);
        runtime.setPreexistingDirtyFiles(List.of());
        runtime.setTaskAgent(null);
        runtime.setCommitAgent(null);
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact(EXTENSION_TARGET, design);
        ctx.putArtifact(RUNTIME_EXTENSION, merged);
        return new VerifiedExtensionTask(design, task, ctx);
    }

    private static List<String> dependencies(ExtensionDesign design) {
        if (design == null || design.getDependsOn() == null) {
            return List.of();
        }
        return design.getDependsOn();
    }

    private static List<ExtensionDesign> selectDesigns(List<ExtensionDesign> designs, int maxTasks) {
        List<ExtensionDesign> constraints = new ArrayList<>();
        List<ExtensionDesign> capabilities = new ArrayList<>();
        for (ExtensionDesign design : safeDesigns(designs)) {
            if ("constraint".equals(design.getKind())) {
                constraints.add(design);
            } else {
                capabilities.add(design);
            }
        }
        List<ExtensionDesign> ordered = new ArrayList<>(constraints);
        ordered.addAll(capabilities);
        int limit = Math.max(0, Math.min(maxTasks, ordered.size()));
        return new ArrayList<>(ordered.subList(0, limit));
    }

    private static List<ExtensionDesign> safeDesigns(List<ExtensionDesign> designs) {
        return designs == null ? List.of() : designs;
    }

    @FunctionalInterface
    interface StageFactory {
        BaseStage create(String stageName);
    }

    @FunctionalInterface
    interface BuildVerifyRunner {
        Iterator<Object> run(
                AutoHarnessOrchestrator orchestrator,
                ExtensionDesign design,
                List<VerifiedExtensionTask> verifiedTasks
        );
    }

    @FunctionalInterface
    interface ActivateRunner {
        Iterator<Object> run(AutoHarnessOrchestrator orchestrator, VerifiedExtensionTask verified);
    }

    @FunctionalInterface
    interface MergeBlockFactory {
        MergeActivationBlock create();
    }

    @FunctionalInterface
    interface SkillSourceEnsurer {
        void ensure(AutoHarnessConfig config);
    }

    private static BaseStage createDefaultStage(String stageName) {
        return switch (stageName) {
            case ASSESS_STAGE_NAME -> instantiateStage(
                    ASSESS_STAGE_CLASS,
                    new DeferredExtendedStage(ASSESS_STAGE_NAME, StageSlot.ASSESS.value())
            );
            case PLAN_STAGE_NAME -> instantiateStage(
                    PLAN_STAGE_CLASS,
                    new DeferredExtendedStage(PLAN_STAGE_NAME, StageSlot.PLAN.value())
            );
            default -> new DeferredExtendedStage(stageName, stageName);
        };
    }

    private static Class<? extends BaseStage> loadStageClass(String className) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class);
        } catch (ClassNotFoundException e) {
            return DeferredExtendedStage.class;
        }
    }

    private static BaseStage instantiateStage(String className, BaseStage fallback) {
        try {
            return Class.forName(className).asSubclass(BaseStage.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    /**
     * Deferred stage binding used only when concrete extended stages have not
     * been translated yet.
     *
     * <p>Mirrors Python's stage import dependency in
     * {@code openjiuwen/auto_harness/pipelines/extended_evolve_pipeline/extended_evolve_pipeline.py}.</p>
     */
    public static class DeferredExtendedStage extends SessionStage {

        private final String name;
        private final String slot;

        public DeferredExtendedStage() {
            this("", "");
        }

        DeferredExtendedStage(String name, String slot) {
            this.name = name == null ? "" : name;
            this.slot = slot == null ? "" : slot;
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
                    .status("failed")
                    .error("Stage unavailable: " + name)
                    .messages(List.of("Stage unavailable: " + name))
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
