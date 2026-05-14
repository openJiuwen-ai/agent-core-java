package com.openjiuwen.auto_harness.orchestrator;

import com.openjiuwen.auto_harness.registry.BuiltinRegistries;
import com.openjiuwen.auto_harness.registry.PipelineRegistry;
import com.openjiuwen.auto_harness.registry.StageRegistry;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessPaths;
import com.openjiuwen.auto_harness.schema.AutoHarnessRuntimeState;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.ProjectProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal top-level orchestrator surface for Java auto_harness.
 *
 * <p>Mirrors the highest-value initialization and state aspects of Python's
 * {@code AutoHarnessOrchestrator} in {@code openjiuwen.auto_harness.orchestrator}.</p>
 */
public class AutoHarnessOrchestrator {

    private final AutoHarnessConfig config;
    private final Object agent;
    private final AutoHarnessPaths paths;
    private final AutoHarnessRuntimeState runtime;
    private final ProjectProfile projectProfile;
    private final StageRegistry stageRegistry;
    private final PipelineRegistry pipelineRegistry;
    private final List<CycleResult> results = new ArrayList<>();
    private CycleResult lastCycleResult = new CycleResult();

    public AutoHarnessOrchestrator(AutoHarnessConfig config, Object agent) {
        this.config = config;
        this.agent = agent;
        this.paths = config.buildPaths();
        this.runtime = new AutoHarnessRuntimeState();
        this.runtime.setCurrentWorkspace(config.getWorkspace());
        this.runtime.setConfigBootstrapped(config.isConfigBootstrapped());
        this.runtime.setSuggestedLocalRepo(config.getSuggestedLocalRepo());
        this.projectProfile = config.buildProjectProfile();
        this.stageRegistry = BuiltinRegistries.buildStageRegistry(config);
        this.pipelineRegistry = BuiltinRegistries.buildPipelineRegistry(config, stageRegistry);
    }

    public static AutoHarnessOrchestrator createAutoHarnessOrchestrator(AutoHarnessConfig config, Object agent) {
        return new AutoHarnessOrchestrator(config, agent);
    }

    public AutoHarnessConfig getConfig() {
        return config;
    }

    public Object getAgent() {
        return agent;
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

    public StageRegistry getStageRegistry() {
        return stageRegistry;
    }

    public PipelineRegistry getPipelineRegistry() {
        return pipelineRegistry;
    }

    public List<CycleResult> getResults() {
        return new ArrayList<>(results);
    }

    public CycleResult getLastCycleResult() {
        return lastCycleResult;
    }

    public void recordCycleResult(CycleResult result) {
        this.lastCycleResult = result;
        this.results.add(result);
    }

    public void resetSessionState() {
        this.results.clear();
        this.lastCycleResult = new CycleResult();
    }
}
