/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.orchestrator;

import com.openjiuwen.auto_harness.artifacts.ArtifactStore;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.infra.PipelineSelector;
import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.auto_harness.infra.WorktreeManager;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.registry.StageRegistry;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessPaths;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessRuntimeState;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ProjectProfile;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session controller and top-level pipeline dispatcher.
 *
 * <p>Mirrors Python's {@code AutoHarnessOrchestrator} in
 * {@code openjiuwen/auto_harness/orchestrator.py}.</p>
 */
public class AutoHarnessOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(AutoHarnessOrchestrator.class.getName());

    private final AutoHarnessConfig config;
    private final DeepAgent agent;
    private final List<AgentRail> streamRails;
    private final AutoHarnessPaths paths;
    private final ProjectProfile projectProfile;
    private final StageRegistry stageRegistry;
    private final PipelineRegistry pipelineRegistry;
    private final Map<String, CompletableFuture<Object>> pendingInteractions = new LinkedHashMap<>();
    private final Map<String, SessionContext> taskContexts = new LinkedHashMap<>();

    private AutoHarnessRuntimeState runtime;
    private ArtifactStore artifacts;
    private ExperienceStore experienceStore;
    private SessionBudgetController budget;
    private FixLoopController fixLoop;
    private WorktreeManager worktreeMgr;
    private GitOperations git;
    private CIGateRunner ciGate;
    private List<CycleResult> results = new ArrayList<>();
    private CycleResult lastCycleResult = new CycleResult();
    private AgentRail cancellationRail;
    private boolean cancelled;

    public AutoHarnessOrchestrator() {
        this(new AutoHarnessConfig());
    }

    public AutoHarnessOrchestrator(AutoHarnessConfig config) {
        this(config, null, List.of());
    }

    public AutoHarnessOrchestrator(AutoHarnessConfig config, DeepAgent agent) {
        this(config, agent, List.of());
    }

    public AutoHarnessOrchestrator(AutoHarnessConfig config, DeepAgent agent, List<? extends AgentRail> streamRails) {
        this.config = config == null ? new AutoHarnessConfig() : config;
        this.streamRails = new ArrayList<>(streamRails == null ? List.of() : streamRails);
        this.agent = agent == null ? inferAgentFromRails(this.streamRails) : agent;
        this.paths = this.config.buildPaths();
        createRuntimeExtensionsDir();
        this.runtime = AutoHarnessRuntimeState.builder()
                .currentWorkspace(this.config.getWorkspace())
                .configBootstrapped(this.config.isConfigBootstrapped())
                .suggestedLocalRepo(this.config.getSuggestedLocalRepo())
                .build();
        this.projectProfile = this.config.buildProjectProfile();
        this.artifacts = new ArtifactStore();
        this.stageRegistry = new StageRegistry();
        this.pipelineRegistry = new PipelineRegistry();
        registerPlaceholderPipelines();
        this.experienceStore = new ExperienceStore(this.config.getResolvedExperienceDir());
        this.budget = new SessionBudgetController(
                this.config.getSessionBudgetSecs(),
                this.config.getCostLimitUsd(),
                this.config.getTaskTimeoutSecs()
        );
        this.fixLoop = new FixLoopController(
                this.config.getFixPhase1MaxRetries(),
                this.config.getFixPhase2MaxRetries(),
                600.0
        );
        this.worktreeMgr = new WorktreeManager(this.config);
        this.git = new GitOperations(
                "",
                this.config.getGitRemote(),
                this.config.getGitBaseBranch(),
                this.config.getForkOwner(),
                this.config.getUpstreamOwner(),
                this.config.getUpstreamRepo(),
                this.config.resolveGitcodeUsername(),
                this.config.resolveGitcodeToken(),
                this.config.getGitUserName(),
                this.config.getGitUserEmail()
        );
        this.ciGate = new CIGateRunner(
                "",
                this.config.getCiGateConfig(),
                this.config.resolveCiGatePythonExecutable(),
                this.config.getCiGateInstallCommand()
        );
        setupCancellationRail();
    }

    public static Iterator<Object> emptyIterator() {
        return Collections.emptyIterator();
    }

    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config) {
        return new AutoHarnessOrchestrator(config);
    }

    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(
            AutoHarnessConfig config,
            DeepAgent agent,
            List<? extends AgentRail> streamRails
    ) {
        return new AutoHarnessOrchestrator(config, agent, streamRails);
    }

    public static String writeDebugArtifact(String runsDir, String filename, String content) {
        Path path = Path.of(runsDir).resolve(filename);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write debug artifact: " + path, e);
        }
    }

    public static DeepAgent inferAgentFromRails(List<? extends AgentRail> streamRails) {
        for (Object rail : streamRails == null ? List.of() : streamRails) {
            Object candidate = readMember(rail, "_deep_agent");
            if (candidate == null) {
                candidate = readMember(rail, "deep_agent");
            }
            if (candidate instanceof DeepAgent deepAgent) {
                return deepAgent;
            }
        }
        return null;
    }

    public void cancel() {
        this.cancelled = true;
        LOGGER.info("[AutoHarnessOrchestrator] cancellation requested");
    }

    public boolean shouldCancel() {
        return cancelled;
    }

    public List<CycleResult> getResults() {
        return new ArrayList<>(results);
    }

    public CycleResult getLastCycleResult() {
        return lastCycleResult;
    }

    public void recordCycleResult(CycleResult result) {
        CycleResult recorded = result == null ? new CycleResult() : result;
        this.lastCycleResult = recorded;
        this.results.add(recorded);
    }

    public OutputSchema messageOutput(String text) {
        return msg(text);
    }

    public CompletableFuture<Object> createInteraction(String interactionId) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingInteractions.put(interactionId, future);
        return future;
    }

    public boolean dispatchMessage(Map<String, Object> message) {
        if (message == null) {
            return false;
        }
        Object interactionId = message.get("interaction_id");
        if (interactionId == null) {
            return false;
        }
        return resolveInteraction(String.valueOf(interactionId), message);
    }

    public boolean resolveInteraction(String interactionId, Object response) {
        CompletableFuture<Object> future = pendingInteractions.remove(interactionId);
        if (future == null || future.isDone()) {
            return false;
        }
        future.complete(response);
        return true;
    }

    public Iterator<Object> runSessionStream(List<OptimizationTask> tasks) {
        return streamSessionPipeline(tasks);
    }

    public Iterator<Object> runSessionStream(List<OptimizationTask> tasks, Map<String, Object> message) {
        if (message != null) {
            dispatchMessage(message);
            return emptyIterator();
        }
        return streamSessionPipeline(tasks);
    }

    public Iterator<Object> streamSessionPipeline(List<OptimizationTask> tasks) {
        List<Object> chunks = new ArrayList<>();
        this.results = new ArrayList<>();
        this.lastCycleResult = new CycleResult();
        this.artifacts = new ArtifactStore();
        this.cancelled = false;
        this.budget.start();
        chunks.add(msg("会话启动"));
        LOGGER.info("[AutoHarnessOrchestrator] session started");

        if (tasks != null) {
            this.artifacts.put("input_tasks", new ArrayList<>(tasks));
        }

        PipelineSelectionArtifact selectedPipeline = selectSessionPipeline(tasks);
        this.runtime.setSelectedPipeline(selectedPipeline.getPipelineName());
        this.artifacts.put("pipeline_selection", selectedPipeline);
        chunks.add(sessionPipelineMessage(selectedPipeline.getPipelineName()));
        Iterator<Object> pipelineStream = runPipelineStream(selectedPipeline.getPipelineName());
        pipelineStream.forEachRemaining(chunks::add);
        chunks.add(harnessSessionFinished(selectedPipeline.getPipelineName()));
        return chunks.iterator();
    }

    public PipelineSelectionArtifact selectSessionPipeline(List<OptimizationTask> tasks) {
        return PipelineSelector.chooseSessionPipeline(
                new ArrayList<>(tasks == null ? List.of() : tasks),
                config,
                pipelineRegistry.names()
        );
    }

    public Path ensureSessionRuntimeDir() {
        Path path = Path.of(paths.getRuntimeExtensionsDir()).resolve(runtime.getSessionId());
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create session runtime dir: " + path, e);
        }
    }

    public Iterator<Object> runPipelineStream(String pipelineName) {
        PipelineSpec spec = pipelineRegistry.require(pipelineName);
        Class<?> pipelineCls = spec.getPipelineCls();
        if (pipelineCls == null || Object.class.equals(pipelineCls)) {
            return emptyIterator();
        }
        try {
            Object pipeline = pipelineCls.getDeclaredConstructor().newInstance();
            Method stream = pipelineCls.getMethod("stream", BaseExecutionContext.class);
            Object result = stream.invoke(pipeline, new SessionContext(this));
            return toIterator(result);
        } catch (NoSuchMethodException e) {
            return emptyIterator();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to run pipeline: " + pipelineName, e);
        }
    }

    public AutoHarnessConfig getConfig() {
        return config;
    }

    public DeepAgent getAgent() {
        return agent;
    }

    public List<AgentRail> getStreamRails() {
        return new ArrayList<>(streamRails);
    }

    public AutoHarnessPaths getPaths() {
        return paths;
    }

    public AutoHarnessRuntimeState getRuntime() {
        return runtime;
    }

    public ProjectProfile getProjectProfile() {
        return projectProfile;
    }

    public ArtifactStore getArtifacts() {
        return artifacts;
    }

    public StageRegistry getStageRegistry() {
        return stageRegistry;
    }

    public PipelineRegistry getPipelineRegistry() {
        return pipelineRegistry;
    }

    public ExperienceStore getExperienceStore() {
        return experienceStore;
    }

    public SessionBudgetController getBudget() {
        return budget;
    }

    public FixLoopController getFixLoop() {
        return fixLoop;
    }

    public WorktreeManager getWorktreeMgr() {
        return worktreeMgr;
    }

    public GitOperations getGit() {
        return git;
    }

    public CIGateRunner getCiGate() {
        return ciGate;
    }

    public Map<String, SessionContext> getTaskContexts() {
        return taskContexts;
    }

    public AgentRail getCancellationRail() {
        return cancellationRail;
    }

    public void setExperienceStore(ExperienceStore experienceStore) {
        this.experienceStore = experienceStore;
    }

    public void setBudget(SessionBudgetController budget) {
        this.budget = budget;
    }

    public void setFixLoop(FixLoopController fixLoop) {
        this.fixLoop = fixLoop;
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

    private void createRuntimeExtensionsDir() {
        String runtimeExtensionsDir = paths.getRuntimeExtensionsDir();
        if (runtimeExtensionsDir == null || runtimeExtensionsDir.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(runtimeExtensionsDir));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create runtime extensions dir", e);
        }
    }

    private void setupCancellationRail() {
        try {
            Class<?> railClass = Class.forName("com.openjiuwen.auto_harness.rails.CancellationRail");
            if (!AgentRail.class.isAssignableFrom(railClass)) {
                return;
            }
            AgentRail rail = (AgentRail) railClass.getDeclaredConstructor().newInstance();
            try {
                railClass.getMethod("bind", AutoHarnessOrchestrator.class).invoke(rail, this);
            } catch (NoSuchMethodException ignored) {
                // The real rail task may expose a different binding surface; T01535 will tighten this.
            }
            cancellationRail = rail;
            streamRails.add(rail);
        } catch (ClassNotFoundException e) {
            LOGGER.info("[AutoHarnessOrchestrator] CancellationRail not translated yet; skipping rail bind");
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "[AutoHarnessOrchestrator] failed to bind CancellationRail", e);
        }
    }

    private void registerPlaceholderPipelines() {
        pipelineRegistry.register(PipelineSpec.builder()
                .name(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
                .pipelineCls(classOrObject(
                        "com.openjiuwen.auto_harness.pipelines.meta_evolve_pipeline.MetaEvolvePipeline"
                ))
                .build());
        pipelineRegistry.register(PipelineSpec.builder()
                .name(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE)
                .pipelineCls(classOrObject(
                        "com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.ExtendedEvolvePipeline"
                ))
                .build());
    }

    private static Class<?> classOrObject(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return Object.class;
        }
    }

    private static OutputSchema msg(String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        return new OutputSchema("message", 0, payload);
    }

    private static OutputSchema sessionPipelineMessage(String pipelineName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", "Session pipeline: " + pipelineName);
        payload.put("pipeline", pipelineName);
        payload.put("stages", List.of());
        return new OutputSchema("message", 0, payload);
    }

    private static OutputSchema harnessSessionFinished(String pipelineName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pipeline", pipelineName);
        payload.put("status", "success");
        payload.put("results_count", 0);
        payload.put("is_terminal", true);
        return new OutputSchema("harness_session_finished", 0, payload);
    }

    private static Iterator<Object> toIterator(Object value) {
        if (value instanceof Iterator<?> iterator) {
            return castIterator(iterator);
        }
        if (value instanceof Iterable<?> iterable) {
            return castIterator(iterable.iterator());
        }
        return emptyIterator();
    }

    private static Iterator<Object> castIterator(Iterator<?> iterator) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next();
            }
        };
    }

    private static Object readMember(Object target, String memberName) {
        if (target == null) {
            return null;
        }
        Object value = readField(target, memberName);
        if (value != null) {
            return value;
        }
        return readGetter(target, memberName);
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object readGetter(Object target, String memberName) {
        String suffix = memberName.startsWith("_") ? memberName.substring(1) : memberName;
        String getter = "get" + Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
