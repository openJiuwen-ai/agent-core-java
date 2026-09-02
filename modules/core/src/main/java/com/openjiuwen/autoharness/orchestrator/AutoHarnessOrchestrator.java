/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.orchestrator;

import com.openjiuwen.autoharness.artifacts.ArtifactStore;
import com.openjiuwen.autoharness.contexts.SessionContext;
import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.infra.CIGateRunner;
import com.openjiuwen.autoharness.infra.FixLoopController;
import com.openjiuwen.autoharness.infra.GitOperations;
import com.openjiuwen.autoharness.infra.SessionBudgetController;
import com.openjiuwen.autoharness.infra.WorktreeManager;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.registry.BuiltinRegistries;
import com.openjiuwen.autoharness.registry.PipelineRegistry;
import com.openjiuwen.autoharness.registry.StageRegistry;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.AutoHarnessPaths;
import com.openjiuwen.autoharness.schema.AutoHarnessRuntimeState;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.PipelineSpec;
import com.openjiuwen.autoharness.schema.ProjectProfile;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public class AutoHarnessOrchestrator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class AutoHarnessOrchestrator {
    private final AutoHarnessConfig config;
    private final StageRegistry stageRegistry;
    private final PipelineRegistry pipelineRegistry;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<CycleResult> results = new ArrayList<>();

    /**
     * ArtifactStore.
     * 
     * @since 0.1.7
     */
    private final ArtifactStore artifacts = new ArtifactStore();
    private final AutoHarnessPaths paths;
    private final AutoHarnessRuntimeState runtime;
    private final ProjectProfile projectProfile;
    private final GitOperations git;
    private final ExperienceStore experienceStore;
    private final SessionBudgetController budget;
    private final FixLoopController fixLoop;
    private final WorktreeManager worktreeMgr;
    private final CIGateRunner ciGate;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, SessionContext> taskContexts = new LinkedHashMap<>();

    /**
     * CycleResult.builder.
     * 
     * @since 0.1.7
     */
    private CycleResult lastCycleResult = CycleResult.builder().build();

    /**
     * AutoHarnessOrchestrator.
     * 
     * @param config config
     * @since 0.1.7
     */
    public AutoHarnessOrchestrator(AutoHarnessConfig config) {
        this.config = config;
        this.stageRegistry = BuiltinRegistries.buildStageRegistry(config);
        this.pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(config, stageRegistry);
        this.paths = config.buildPaths();
        this.runtime = AutoHarnessRuntimeState.builder().currentWorkspace(config.getWorkspace())
                .isConfigBootstrapped(config.isConfigBootstrapped()).suggestedLocalRepo(config.getSuggestedLocalRepo())
                .build();
        this.projectProfile = config.buildProjectProfile();
        this.git = new GitOperations("", config.getGitRemote(), config.getGitBaseBranch(), config.getForkOwner(),
                config.getUpstreamOwner(), config.getUpstreamRepo(), config.resolveGitcodeUsername(),
                config.resolveGitcodeToken(), config.getGitUserName(), config.getGitUserEmail());
        this.experienceStore = new ExperienceStore(config.experiencePath().toString());
        this.budget = new SessionBudgetController(config.getSessionBudgetSecs(), config.getCostLimitUsd(),
                config.getTaskTimeoutSecs());
        this.fixLoop = new FixLoopController(config.getFixPhase1MaxRetries(), config.getFixPhase2MaxRetries());
        this.worktreeMgr = new WorktreeManager(config);
        this.ciGate = new CIGateRunner("", config.getCiGateConfig(), config.resolveCiGatePythonExecutable(),
                config.getCiGateInstallCommand());
    }

    /**
     * runSession.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    public List<CycleResult> runSession(List<OptimizationTask> tasks) {
        runSessionStream(tasks);
        return List.copyOf(results);
    }

    /**
     * runSessionStream.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    public List<Object> runSessionStream(List<OptimizationTask> tasks) {
        List<OptimizationTask> effectiveTasks = tasks == null ? List.of() : List.copyOf(tasks);
        results.clear();
        lastCycleResult = CycleResult.builder().build();
        taskContexts.clear();
        budget.start();
        if (tasks != null) {
            artifacts.put("input_tasks", effectiveTasks, "");
        }
        PipelineSelectionArtifact selection = selectPipeline(effectiveTasks);
        runtime.setSelectedPipeline(selection.getPipelineName());
        artifacts.put("pipeline_selection", selection, "");
        PipelineSpec pipeline = pipelineRegistry.require(selection.getPipelineName());
        BasePipelineInstance pipelineInstance = instantiatePipeline(pipeline);
        SessionContext sessionContext = new SessionContext(this);
        List<Object> events = new ArrayList<>();
        events.add(SessionContext.message("会话启动"));
        events.add(SessionContext.message("Session pipeline: " + selection.getPipelineName()));
        events.addAll(pipelineInstance.stream(sessionContext));
        return List.copyOf(events);
    }

    /**
     * selectPipeline.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    public PipelineSelectionArtifact selectPipeline(List<OptimizationTask> tasks) {
        List<String> available = new ArrayList<>(pipelineRegistry.names());
        if (available.isEmpty()) {
            throw new IllegalArgumentException("No pipelines registered for auto-harness session");
        }

        Set<String> explicit = (tasks == null ? List.<OptimizationTask>of() : tasks).stream()
                .map(OptimizationTask::getPipelineName).filter(this::hasText).map(this::normalizePipelineName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (explicit.size() > 1) {
            throw new IllegalArgumentException(
                    "Conflicting task pipeline_name values in one session: " + String.join(", ", explicit));
        }

        String selected;
        String reason;
        if (!explicit.isEmpty()) {
            selected = explicit.iterator().next();
            reason = "tasks requested explicit pipeline";
        } else if (available.size() == 1) {
            selected = available.get(0);
            reason = "single registered pipeline";
        } else if (available.contains(MetaEvolvePipeline.NAME)) {
            selected = MetaEvolvePipeline.NAME;
            reason = "default session pipeline";
        } else {
            selected = available.get(0);
            reason = "fallback to first registered pipeline";
        }

        if (!available.contains(selected)) {
            String fallback = available.contains(MetaEvolvePipeline.NAME) ? MetaEvolvePipeline.NAME : available.get(0);
            return PipelineSelectionArtifact.builder().pipelineName(fallback)
                    .reason("requested session pipeline unsupported, fallback to " + fallback).confidence(0.0)
                    .fallbackPipeline(fallback).build();
        }

        List<String> alternatives = available.stream().filter(name -> !name.equals(selected)).toList();
        return PipelineSelectionArtifact.builder().pipelineName(selected).reason(reason).alternatives(alternatives)
                .confidence(1.0).fallbackPipeline(selected).build();
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AutoHarnessConfig getConfig() {
        return config;
    }

    /**
     * getStageRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StageRegistry getStageRegistry() {
        return stageRegistry;
    }

    /**
     * getPipelineRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PipelineRegistry getPipelineRegistry() {
        return pipelineRegistry;
    }

    /**
     * getResults.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<CycleResult> getResults() {
        return List.copyOf(results);
    }

    /**
     * getArtifacts.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ArtifactStore getArtifacts() {
        return artifacts;
    }

    /**
     * getPaths.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AutoHarnessPaths getPaths() {
        return paths;
    }

    /**
     * getRuntime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AutoHarnessRuntimeState getRuntime() {
        return runtime;
    }

    /**
     * getProjectProfile.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ProjectProfile getProjectProfile() {
        return projectProfile;
    }

    /**
     * getGit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public GitOperations getGit() {
        return git;
    }

    /**
     * getExperienceStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ExperienceStore getExperienceStore() {
        return experienceStore;
    }

    /**
     * getBudget.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SessionBudgetController getBudget() {
        return budget;
    }

    /**
     * getFixLoop.
     * 
     * @return the result
     * @since 0.1.7
     */
    public FixLoopController getFixLoop() {
        return fixLoop;
    }

    /**
     * getWorktreeMgr.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorktreeManager getWorktreeMgr() {
        return worktreeMgr;
    }

    /**
     * getCiGate.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CIGateRunner getCiGate() {
        return ciGate;
    }

    /**
     * getTaskContexts.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, SessionContext> getTaskContexts() {
        return taskContexts;
    }

    /**
     * getLastCycleResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CycleResult getLastCycleResult() {
        return lastCycleResult;
    }

    /**
     * recordCycleResult.
     * 
     * @param result result
     * @since 0.1.7
     */
    public void recordCycleResult(CycleResult result) {
        lastCycleResult = result;
        results.add(result);
    }

    /**
     * instantiatePipeline.
     * 
     * @param pipeline pipeline
     * @return the result
     * @since 0.1.7
     */
    private BasePipelineInstance instantiatePipeline(PipelineSpec pipeline) {
        if (pipeline.getPipelineCls() == null) {
            throw new IllegalArgumentException("Pipeline '" + pipeline.getName() + "' has no pipeline class");
        }
        try {
            return pipeline.getPipelineCls().getDeclaredConstructor().newInstance()::stream;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate pipeline '" + pipeline.getName() + "'", e);
        }
    }

    /**
     * normalizePipelineName.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private String normalizePipelineName(String name) {
        if (!hasText(name)) {
            return "";
        }
        return switch (name.trim()) {
            case "pr_pipeline" -> MetaEvolvePipeline.NAME;
            case "extended_harness_pipeline" -> "extended_evolve_pipeline";
            default -> name.trim();
        };
    }

    /**
     * hasText.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface BasePipelineInstance {
        /**
         * stream.
         * 
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        List<Object> stream(SessionContext context);
    }
}
