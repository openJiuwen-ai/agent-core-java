/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.orchestrator;

import com.openjiuwen.auto_harness.artifacts.ArtifactStore;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.pipelines.BasePipeline;
import com.openjiuwen.auto_harness.registry.BuiltinRegistries;
import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.registry.StageRegistry;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessPaths;
import com.openjiuwen.auto_harness.schema.AutoHarnessRuntimeState;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.ProjectProfile;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Session controller and top-level pipeline dispatcher.
 *
 * <p>Mirrors Python's {@code AutoHarnessOrchestrator} in {@code openjiuwen.auto_harness.orchestrator}.</p>
 */
public class AutoHarnessOrchestrator {

    private static final Logger logger = Logger.getLogger(AutoHarnessOrchestrator.class.getName());
    private static final String META_EVOLVE_PIPELINE = AutoHarnessPipelineNames.META_EVOLVE_PIPELINE;

    private final AutoHarnessConfig config;
    private final Object agent;
    private final AutoHarnessPaths paths;
    private AutoHarnessRuntimeState runtime;
    private final ProjectProfile projectProfile;
    private final StageRegistry stageRegistry;
    private final PipelineRegistry pipelineRegistry;
    private final List<CycleResult> results = new ArrayList<>();
    private CycleResult lastCycleResult = new CycleResult();

    // Additional infrastructure components
    private ArtifactStore artifacts;
    private ExperienceStore experienceStore;
    private SessionBudgetController budget;
    private FixLoopController fixLoop;
    private WorktreeManager worktreeMgr;
    private GitOperations git;
    private CIGateRunner ciGate;
    private Map<String, SessionContext> taskContexts = new HashMap<>();

    public AutoHarnessOrchestrator(AutoHarnessConfig config, Object agent) {
        this.config = config;
        this.agent = agent;
        this.paths = config.buildPaths();

        // Initialize runtime state
        this.runtime = new AutoHarnessRuntimeState();
        this.runtime.setCurrentWorkspace(config.getWorkspace());
        this.runtime.setConfigBootstrapped(config.isConfigBootstrapped());
        this.runtime.setSuggestedLocalRepo(config.getSuggestedLocalRepo());

        this.projectProfile = config.buildProjectProfile();
        this.stageRegistry = BuiltinRegistries.buildStageRegistry(config);
        this.pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(config, stageRegistry);

        // Initialize artifacts store
        this.artifacts = new ArtifactStore();

        // Initialize experience store
        this.experienceStore = new ExperienceStore(config.getResolvedExperienceDir());

        // Initialize budget controller
        this.budget = new SessionBudgetController(
                config.getSessionBudgetSecs(),
                config.getCostLimitUsd(),
                config.getTaskTimeoutSecs()
        );

        // Initialize fix loop controller
        this.fixLoop = new FixLoopController(
                config.getFixPhase1MaxRetries(),
                config.getFixPhase2MaxRetries(),
                600.0
        );

        // Initialize worktree manager
        this.worktreeMgr = new WorktreeManager(config);

        // Initialize git operations
        this.git = new GitOperations(
                "",
                config.getGitRemote(),
                config.getGitBaseBranch(),
                config.getForkOwner(),
                config.getUpstreamOwner(),
                config.getUpstreamRepo(),
                config.resolveGitcodeUsername(),
                config.resolveGitcodeToken(),
                config.getGitUserName(),
                config.getGitUserEmail()
        );

        // Initialize CI gate runner
        this.ciGate = new CIGateRunner(
                "",
                config.getCiGateConfig(),
                config.resolveCiGatePythonExecutable(),
                config.getCiGateInstallCommand()
        );
    }

    /**
     * Create an orchestrator instance without agent.
     *
     * @param config the auto harness configuration
     * @return the orchestrator instance
     */
    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config) {
        return new AutoHarnessOrchestrator(config, null);
    }

    /**
     * Create an orchestrator instance with agent.
     *
     * @param config the auto harness configuration
     * @param agent  the deep agent
     * @return the orchestrator instance
     */
    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config, Object agent) {
        return new AutoHarnessOrchestrator(config, agent);
    }

    /**
     * Construct a message OutputSchema.
     *
     * @param text the message text
     * @return an OutputSchema with message type
     */
    private static OutputSchema msg(String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", text);
        return new OutputSchema("message", 0, payload);
    }

    /**
     * Get the configuration.
     *
     * @return the auto harness configuration
     */
    public AutoHarnessConfig getConfig() {
        return config;
    }

    /**
     * Get the agent.
     *
     * @return the deep agent (may be null)
     */
    public Object getAgent() {
        return agent;
    }

    /**
     * Get the paths.
     *
     * @return the auto harness paths
     */
    public AutoHarnessPaths getPaths() {
        return paths;
    }

    /**
     * Get the runtime state.
     *
     * @return the runtime state
     */
    public AutoHarnessRuntimeState getRuntime() {
        return runtime;
    }

    /**
     * Get the project profile.
     *
     * @return the project profile
     */
    public ProjectProfile getProjectProfile() {
        return projectProfile;
    }

    /**
     * Get the stage registry.
     *
     * @return the stage registry
     */
    public StageRegistry getStageRegistry() {
        return stageRegistry;
    }

    /**
     * Get the pipeline registry.
     *
     * @return the pipeline registry
     */
    public PipelineRegistry getPipelineRegistry() {
        return pipelineRegistry;
    }

    /**
     * Get the session results.
     *
     * @return a copy of the results list
     */
    public List<CycleResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Get the last cycle result.
     *
     * @return the last cycle result
     */
    public CycleResult getLastCycleResult() {
        return lastCycleResult;
    }

    /**
     * Get the artifacts store.
     *
     * @return the artifacts store
     */
    public ArtifactStore getArtifacts() {
        return artifacts;
    }

    /**
     * Get the experience store.
     *
     * @return the experience store
     */
    public ExperienceStore getExperienceStore() {
        return experienceStore;
    }

    /**
     * Get the budget controller.
     *
     * @return the budget controller
     */
    public SessionBudgetController getBudget() {
        return budget;
    }

    /**
     * Get the fix loop controller.
     *
     * @return the fix loop controller
     */
    public FixLoopController getFixLoop() {
        return fixLoop;
    }

    /**
     * Get the worktree manager.
     *
     * @return the worktree manager
     */
    public WorktreeManager getWorktreeMgr() {
        return worktreeMgr;
    }

    /**
     * Get the git operations.
     *
     * @return the git operations
     */
    public GitOperations getGit() {
        return git;
    }

    /**
     * Get the CI gate runner.
     *
     * @return the CI gate runner
     */
    public CIGateRunner getCiGate() {
        return ciGate;
    }

    /**
     * Get the task contexts map.
     *
     * @return the task contexts map
     */
    public Map<String, SessionContext> getTaskContexts() {
        return taskContexts;
    }

    public void setExperienceStore(ExperienceStore experienceStore) {
        this.experienceStore = experienceStore;
    }

    public void setWorktreeMgr(WorktreeManager worktreeMgr) {
        this.worktreeMgr = worktreeMgr;
    }

    public void setGit(GitOperations git) {
        this.git = git;
    }

    public void setCiGate(CIGateRunner ciGate) {
        this.ciGate = ciGate;
    }

    /**
     * Persist one task cycle result on the orchestrator.
     *
     * @param result the cycle result
     */
    public void recordCycleResult(CycleResult result) {
        this.lastCycleResult = result;
        this.results.add(result);
    }

    /**
     * Construct a message OutputSchema.
     *
     * @param text the message text
     * @return an OutputSchema with message type
     */
    public OutputSchema messageOutput(String text) {
        return msg(text);
    }

    /**
     * Reset session state for a new session.
     */
    public void resetSessionState() {
        this.results.clear();
        this.lastCycleResult = new CycleResult();
        this.artifacts = new ArtifactStore();
        this.taskContexts.clear();
    }

    /**
     * Start the budget timer.
     */
    public void startBudget() {
        this.budget.start();
    }

    /**
     * Stream session execution as OutputSchema chunks.
     *
     * <p>Mirrors Python's {@code run_session_stream}.</p>
     *
     * @param tasks optional direct task list; null means run assess and plan first
     * @return an iterator over streamed events and results
     */
    public Iterator<Object> runSessionStream(List<OptimizationTask> tasks) {
        List<Object> chunks = new ArrayList<>();
        resetSessionState();
        budget.start();
        chunks.add(msg("会话启动"));
        logger.info("Session started");

        if (tasks != null) {
            artifacts.put("input_tasks", new ArrayList<>(tasks));
        }

        PipelineSelectionArtifact selectedPipeline = selectSessionPipeline(tasks);
        runtime.setSelectedPipeline(selectedPipeline.getPipelineName());
        artifacts.put("pipeline_selection", selectedPipeline);
        chunks.add(msg("Session pipeline: " + selectedPipeline.getPipelineName()));

        Iterator<Object> pipelineStream = runPipelineStream(selectedPipeline.getPipelineName());
        pipelineStream.forEachRemaining(chunks::add);
        logger.info("Session finished: " + results.size() + " tasks executed");
        return chunks.iterator();
    }

    /**
     * Execute a registered top-level pipeline.
     *
     * <p>Mirrors Python's {@code _run_pipeline_stream}.</p>
     *
     * @param pipelineName the registered pipeline name
     * @return an iterator over pipeline events
     */
    public Iterator<Object> runPipelineStream(String pipelineName) {
        List<Object> chunks = new ArrayList<>();
        PipelineSpec spec = pipelineRegistry.require(pipelineName);
        try {
            Object instance = spec.getPipelineClass().getDeclaredConstructor().newInstance();
            if (!(instance instanceof BasePipeline pipeline)) {
                throw new IllegalStateException("Registered pipeline is not a BasePipeline: " + pipelineName);
            }
            pipeline.execute(new SessionContext(this), chunks::add);
            return chunks.iterator();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create pipeline: " + pipelineName, e);
        }
    }

    /**
     * Select the session pipeline before any concrete pipeline runs.
     *
     * @param tasks the optional list of optimization tasks
     * @return the pipeline selection artifact
     */
    public PipelineSelectionArtifact selectSessionPipeline(List<OptimizationTask> tasks) {
        List<String> available = pipelineRegistry.names();
        if (available.isEmpty()) {
            throw new IllegalStateException("No pipelines registered for auto-harness session");
        }

        // Extract explicit pipeline names from tasks
        List<String> explicit = new ArrayList<>();
        if (tasks != null) {
            for (OptimizationTask task : tasks) {
                String pipelineName = task.getPipelineName();
                if (pipelineName != null && !pipelineName.isEmpty()) {
                    String normalized = AutoHarnessPipelineNames.normalizePipelineName(pipelineName);
                    if (!explicit.contains(normalized)) {
                        explicit.add(normalized);
                    }
                }
            }
        }

        if (explicit.size() > 1) {
            throw new IllegalStateException(
                    "Conflicting task pipeline_name values in one session: " + String.join(", ", explicit)
            );
        }

        String selected;
        String reason;

        if (!explicit.isEmpty()) {
            selected = explicit.get(0);
            reason = "tasks requested explicit pipeline";
        } else if (available.size() == 1) {
            selected = available.get(0);
            reason = "single registered pipeline";
        } else if (available.contains(META_EVOLVE_PIPELINE)) {
            selected = META_EVOLVE_PIPELINE;
            reason = "default session pipeline";
        } else {
            selected = available.get(0);
            reason = "fallback to first registered pipeline";
        }

        // Check if selected is available
        if (!available.contains(selected)) {
            String fallback = available.contains(META_EVOLVE_PIPELINE)
                    ? META_EVOLVE_PIPELINE
                    : available.get(0);
            return new PipelineSelectionArtifact(
                    fallback,
                    "requested session pipeline unsupported, fallback to " + fallback,
                    0.0,
                    fallback
            );
        }

        // Build alternatives list
        List<String> alternatives = new ArrayList<>();
        for (String name : available) {
            if (!name.equals(selected)) {
                alternatives.add(name);
            }
        }

        return new PipelineSelectionArtifact(
                selected,
                reason,
                1.0,
                selected,
                alternatives
        );
    }

    /**
     * Normalize a pipeline name for comparison.
     *
     * @param name the pipeline name
     * @return the normalized name
     */
    /**
     * Write debug artifact to runs directory.
     *
     * @param runsDir  the runs directory
     * @param filename the filename
     * @param content  the content
     * @return the file path
     */
    public static String writeDebugArtifact(String runsDir, String filename, String content) {
        try {
            Path path = Paths.get(runsDir, filename);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write debug artifact", e);
        }
    }
}
