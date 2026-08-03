/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionLoader;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Stateful DeepAgent runtime facade.
 *
 * <p>Mirrors Python's {@code DeepAgent} in
 * {@code openjiuwen/harness/deep_agent.py}.</p>
 */
public class DeepAgent {

    private final AgentCard card;
    private DeepAgentConfig config = new DeepAgentConfig();
    private Object sessionToolkit;
    private Object reactAgent;
    private boolean initialized;
    private boolean invokeActive;
    private boolean autoInvokeScheduled;
    private final AbilityManager abilityManager = new AbilityManager();
    private final List<DeepAgentRail> rails = new ArrayList<>();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, DeepAgentConfig.SubAgentConfig> subagents = new LinkedHashMap<>();
    private final List<String> pendingHarnessConfigs = new ArrayList<>();
    private final LoopCoordinator loopCoordinator = new LoopCoordinator();
    private final TaskLoopController loopController = new TaskLoopController();
    private final TaskLoopEventHandler eventHandler = new TaskLoopEventHandler(this);

    public DeepAgent() {
        this(new AgentCard("deep_agent", "deep_agent", "DeepAgent"));
    }

    public DeepAgent(AgentCard card) {
        this.card = card == null ? new AgentCard("deep_agent", "deep_agent", "DeepAgent") : card;
    }

    public AgentCard getCard() {
        return card;
    }

    public void setSessionToolkit(Object toolkit) {
        this.sessionToolkit = toolkit;
        eventHandler.setSessionToolkit(toolkit);
    }

    public Object getSessionToolkit() {
        return sessionToolkit;
    }

    public void configure(DeepAgentConfig config) {
        DeepAgentConfig resolved = config == null ? new DeepAgentConfig() : config;
        if (!initialized) {
            initialConfigure(resolved);
        } else {
            hotReconfigure(resolved);
        }
    }

    public DeepAgentConfig deepConfig() {
        return config;
    }

    public Object reactAgent() {
        return reactAgent;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityManager get_ability_manager() {
        return abilityManager;
    }

    public void setReactAgent(Object reactAgent, boolean initialized) {
        this.reactAgent = reactAgent;
        this.initialized = initialized;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isInvokeActive() {
        return invokeActive;
    }

    public boolean isAutoInvokeScheduled() {
        return autoInvokeScheduled;
    }

    public void setAutoInvokeScheduled(boolean autoInvokeScheduled) {
        this.autoInvokeScheduled = autoInvokeScheduled;
    }

    public LoopCoordinator loopCoordinator() {
        return loopCoordinator;
    }

    public TaskLoopController loopController() {
        return loopController;
    }

    public TaskLoopEventHandler eventHandler() {
        return eventHandler;
    }

    public List<DeepAgentRail> getRails() {
        return new ArrayList<>(rails);
    }

    public Map<String, Tool> getTools() {
        return new LinkedHashMap<>(tools);
    }

    public Map<String, DeepAgentConfig.SubAgentConfig> getSubagents() {
        return new LinkedHashMap<>(subagents);
    }

    /**
     * Schedule a harness_config.yaml for loading on the next normal stream call.
     *
     * <p>Mirrors Python's {@code DeepAgent.enqueue_harness_config} in
     * {@code openjiuwen/harness/deep_agent.py}.</p>
     *
     * @param configPath absolute or relative harness config path
     */
    public void enqueueHarnessConfig(String configPath) {
        pendingHarnessConfigs.add(configPath == null ? "" : configPath);
    }

    /**
     * Return pending harness configs in FIFO order.
     *
     * <p>Mirrors Python's {@code DeepAgent._pending_harness_configs} in
     * {@code openjiuwen/harness/deep_agent.py}.</p>
     *
     * @return queued harness config paths
     */
    public List<String> getPendingHarnessConfigs() {
        return new ArrayList<>(pendingHarnessConfigs);
    }

    /**
     * Hot-load resources declared by a harness config file.
     *
     * <p>Mirrors Python's {@code DeepAgent.load_harness_config} in
     * {@code openjiuwen/harness/deep_agent.py}.</p>
     *
     * @param configPath path to {@code harness_config.yaml}
     * @return loaded resource descriptors
     */
    public List<String> loadHarnessConfig(String configPath) {
        Path configPathObj = Path.of(configPath).toAbsolutePath().normalize();
        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPathObj);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null) {
            return List.of();
        }

        List<String> loaded = new ArrayList<>();
        RuntimeExtensionArtifact runtimeExt = runtimeExtensionArtifactForConfig(configPathObj, resources);
        loadRails(resources, runtimeExt, loaded);
        loadTools(resources, runtimeExt, loaded);
        loadSkillDirs(configPathObj, resources, runtimeExt, loaded);
        return loaded;
    }

    public List<String> load_harness_config(String configPath) {
        return loadHarnessConfig(configPath);
    }

    /**
     * Unload resources declared by a harness config file.
     *
     * <p>Mirrors Python's {@code DeepAgent.unload_harness_config} in
     * {@code openjiuwen/harness/deep_agent.py}.</p>
     *
     * @param configPath path to {@code harness_config.yaml}
     * @return unloaded resource descriptors
     */
    public List<String> unloadHarnessConfig(String configPath) {
        Path configPathObj = Path.of(configPath).toAbsolutePath().normalize();
        if (!Files.exists(configPathObj)) {
            throw new IllegalArgumentException("Harness config file not found: " + configPathObj);
        }
        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(configPathObj);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null) {
            return List.of();
        }

        List<String> unloaded = new ArrayList<>();
        RuntimeExtensionArtifact runtimeExt = runtimeExtensionArtifactForConfig(configPathObj, resources);
        unloadRails(resources, runtimeExt, unloaded);
        unloadTools(resources, runtimeExt, unloaded);
        unloadSkillDirs(configPathObj, resources, runtimeExt, unloaded);
        return unloaded;
    }

    public List<String> unload_harness_config(String configPath) {
        return unloadHarnessConfig(configPath);
    }

    public void addRail(DeepAgentRail rail) {
        if (rail == null || rails.contains(rail)) {
            return;
        }
        rail.init(this);
        rails.add(rail);
    }

    public List<DeepAgentRail> findRailsByType(Class<? extends DeepAgentRail> railType) {
        if (railType == null) {
            return List.of();
        }
        return rails.stream().filter(railType::isInstance).toList();
    }

    public int stripRailsByType(Class<? extends DeepAgentRail> railType) {
        if (railType == null) {
            return 0;
        }
        List<DeepAgentRail> removed = rails.stream().filter(railType::isInstance).toList();
        removed.forEach(rail -> rail.uninit(this));
        rails.removeAll(removed);
        return removed.size();
    }

    public CompletableFuture<Void> registerRail(DeepAgentRail rail) {
        addRail(rail);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> unregisterRail(DeepAgentRail rail) {
        if (rails.remove(rail) && rail != null) {
            rail.uninit(this);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void registerTool(Tool tool) {
        if (tool != null && tool.getCard() != null) {
            tools.put(tool.getCard().getName(), tool);
            abilityManager.add(tool.getCard());
        }
    }

    public void unregisterTool(String toolName) {
        tools.remove(toolName);
        abilityManager.remove(toolName);
    }

    public DeepAgent createSubagent(String subagentType, String subsessionId) {
        DeepAgentConfig.SubAgentConfig spec = subagents.get(subagentType);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown subagent type: " + subagentType);
        }
        AgentCard subCard = spec.getCard();
        if (subCard == null) {
            subCard = new AgentCard(subagentType, subagentType, spec.getDescription());
        }
        DeepAgent agent = new DeepAgent(subCard);
        agent.configure(resolveSubagentConfig(spec));
        return agent;
    }

    private DeepAgentConfig resolveSubagentConfig(DeepAgentConfig.SubAgentConfig spec) {
        if (spec.getConfig() != null) {
            return spec.getConfig();
        }
        DeepAgentConfig resolved = new DeepAgentConfig();
        resolved.setModel(spec.getModel() == null ? config.getModel() : spec.getModel());
        resolved.setCard(spec.getCard() == null ? config.getCard() : spec.getCard());
        resolved.setSystemPrompt(spec.getSystemPrompt() == null ? config.getSystemPrompt() : spec.getSystemPrompt());
        resolved.setTools(spec.getTools().isEmpty() ? config.getTools() : spec.getTools());
        resolved.setMcps(spec.getMcps().isEmpty() ? config.getMcps() : spec.getMcps());
        resolved.setSkills(spec.getSkills().isEmpty() ? config.getSkills() : spec.getSkills());
        resolved.setRails(spec.getRails().isEmpty() ? config.getRails() : spec.getRails());
        resolved.setWorkspace(spec.getWorkspace() == null ? config.getWorkspace() : spec.getWorkspace());
        resolved.setBackend(spec.getBackend() == null ? config.getBackend() : spec.getBackend());
        resolved.setSysOperation(spec.getSysOperation() == null ? config.getSysOperation() : spec.getSysOperation());
        resolved.setLanguage(spec.getLanguage() == null ? config.getLanguage() : spec.getLanguage());
        resolved.setPromptMode(spec.getPromptMode() == null ? config.getPromptMode() : spec.getPromptMode());
        resolved.setEnableTaskLoop(spec.isEnableTaskLoop() || config.isEnableTaskLoop());
        resolved.setMaxIterations(spec.getMaxIterations() == null ? config.getMaxIterations() : spec.getMaxIterations());
        resolved.setSubagents(config.getSubagents());
        return resolved;
    }

    public Map<String, Object> normalizeInputs(Map<String, Object> inputs) {
        return inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }

    public CompletableFuture<Map<String, Object>> invoke(Map<String, Object> inputs) {
        return invoke(inputs, null);
    }

    public CompletableFuture<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        if (config != null && config.isEnableTaskLoop() && session != null && reactAgent != null) {
            Map<String, Object> baseInputs = normalizeInputs(inputs);
            if (isResumeInput(baseInputs)) {
                invokeActive = true;
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return invokeReactAgent(reactAgent, baseInputs, session);
                    } finally {
                        invokeActive = false;
                    }
                });
            }
            invokeActive = true;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return runTaskLoop(inputs, session);
                } finally {
                    invokeActive = false;
                }
            });
        }
        return invokeSingleRound(inputs, session);
    }

    private static boolean isResumeInput(Map<String, Object> inputs) {
        return inputs != null && inputs.get("query") instanceof InteractiveInput;
    }

    private CompletableFuture<Map<String, Object>> invokeSingleRound(Map<String, Object> inputs, AgentSessionApi session) {
        invokeActive = true;
        try {
            Map<String, Object> effectiveInputs = normalizeInputs(inputs);
            CallbackContext context = new CallbackContext(this, effectiveInputs);
            for (DeepAgentRail rail : rails) {
                rail.beforeInvoke(context);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "deep_agent_result");
            result.put("input", effectiveInputs);
            Object stateSession = session != null ? session : effectiveInputs.get("session");
            result.put("mode", loadState(stateSession).getPlanMode().getMode());
            for (int i = rails.size() - 1; i >= 0; i--) {
                DeepAgentRail rail = rails.get(i);
                rail.afterInvoke(context);
            }
            return CompletableFuture.completedFuture(result);
        } finally {
            invokeActive = false;
        }
    }

    private Map<String, Object> runTaskLoop(Map<String, Object> inputs, AgentSessionApi session) {
        Map<String, Object> baseInputs = normalizeInputs(inputs);
        String originalQuery = stringValue(baseInputs.get("query"));
        String currentQuery = originalQuery;
        Map<String, Object> lastResult = new LinkedHashMap<>();
        int maxRounds = Math.max(1, config.getMaxIterations());
        loopCoordinator.reset();

        for (int round = 0; round < maxRounds && loopCoordinator.shouldContinue(); round++) {
            DeepAgentState state = loadState(session);
            List<String> newFollowUps = loopController.drainFollowUp();
            if (!newFollowUps.isEmpty()) {
                state.addPendingFollowUps(newFollowUps);
            }

            boolean followUpRound = state.hasPendingFollowUps();
            if (followUpRound) {
                currentQuery = state.pollPendingFollowUp();
            }

            TaskPlan plan = state.getTaskPlan();
            TodoItem task = !followUpRound && plan != null ? plan.getNextTask() : null;
            if (task != null) {
                plan.markInProgress(task.getId());
            }
            saveState(session, state);

            Map<String, Object> effectiveInputs = new LinkedHashMap<>(baseInputs);
            effectiveInputs.put("query", currentQuery);
            effectiveInputs.put("is_follow_up", followUpRound);
            if (session.getSessionId() != null && !session.getSessionId().isBlank()) {
                effectiveInputs.put("conversation_id", session.getSessionId());
            }
            Queue<String> steeringQueue = loopController.getInteractionQueues().steering();
            effectiveInputs.put("_steering_queue", steeringQueue);

            lastResult = invokeReactAgent(reactAgent, effectiveInputs, session);

            state = loadState(session);
            if (task != null && state.getTaskPlan() != null) {
                state.getTaskPlan().markCompleted(task.getId(), stringValue(lastResult.get("output")));
            }
            loopCoordinator.incrementIteration();
            loopCoordinator.setLastResult(lastResult);
            state.setStopConditionState(loopCoordinator.getState());
            saveState(session, state);

            if ("interrupt".equals(lastResult.get("result_type"))) {
                break;
            }
            if (loopCoordinator.isAborted()) {
                break;
            }
            if (loopController.hasFollowUp() || loadState(session).hasPendingFollowUps()) {
                continue;
            }
            if (!hasRemainingTasks(session)) {
                break;
            }
            currentQuery = originalQuery;
        }

        DeepAgentState state = loadState(session);
        state.setStopConditionState(null);
        saveState(session, state);
        return lastResult;
    }

    public Iterator<Map<String, Object>> stream(Map<String, Object> inputs) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("type", "deep_agent_chunk");
        chunk.put("input", normalizeInputs(inputs));
        chunk.put("final", true);
        return List.of(chunk).iterator();
    }

    public Iterator<Map<String, Object>> stream(Map<String, Object> inputs, AgentSessionApi session) {
        return stream(inputs);
    }

    public CompletableFuture<Map<String, Object>> followUp(String message, String taskId, Object session) {
        if (message != null && !message.isBlank() && loopController != null) {
            loopController.enqueueFollowUp(message);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "follow_up");
        result.put("message", message);
        result.put("task_id", taskId);
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<Map<String, Object>> followUp(String message, Object session) {
        return followUp(message, null, session);
    }

    public CompletableFuture<Map<String, Object>> steer(String message, Object session) {
        if (message != null && !message.isBlank() && loopController != null) {
            loopController.getInteractionQueues().pushSteer(message);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "steer");
        result.put("message", message);
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<Boolean> abort(Object session) {
        loopCoordinator.requestAbort();
        invokeActive = false;
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    public DeepAgentState loadState(Object session) {
        if (!(session instanceof AgentSessionApi agentSession)) {
            return new DeepAgentState();
        }
        Object data = agentSession.getState(DeepAgentState.SESSION_STATE_KEY);
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return DeepAgentState.fromSessionMap(normalized);
        }
        return new DeepAgentState();
    }

    public void saveState(Object session, DeepAgentState state) {
        if (!(session instanceof AgentSessionApi agentSession)) {
            return;
        }
        DeepAgentState target = Objects.requireNonNullElseGet(state, DeepAgentState::new);
        agentSession.updateState(Map.of(DeepAgentState.SESSION_STATE_KEY, target.toSessionMap()));
    }

    public void clearState(Object session, boolean clearPersisted) {
        loopCoordinator.reset();
        if (clearPersisted && session instanceof AgentSessionApi agentSession) {
            Map<String, Object> cleared = new LinkedHashMap<>();
            cleared.put(DeepAgentState.SESSION_STATE_KEY, null);
            agentSession.updateState(cleared);
        }
    }

    public void clearState(Object session) {
        clearState(session, false);
    }

    public void switchMode(Object session, AgentMode mode) {
        DeepAgentState state = loadState(session);
        state.getPlanMode().setMode(mode == null ? AgentMode.NORMAL.value() : mode.value());
        saveState(session, state);
    }

    public String getPlanFilePath(Object session) {
        DeepAgentState state = loadState(session);
        String slug = state.getPlanMode().getPlanSlug();
        if (slug == null || slug.isBlank()) {
            return null;
        }
        Path slugPath = Path.of(slug);
        if (slugPath.isAbsolute() || slug.contains("/") || slug.contains("\\") || slug.endsWith(".md")) {
            return slugPath.toString();
        }
        Path root = workspaceRootPath();
        if (root == null) {
            return slug;
        }
        return root.resolve(".plans").resolve(slug + ".md").normalize().toString();
    }

    private Path workspaceRootPath() {
        Object workspace = config == null ? null : config.getWorkspace();
        if (workspace instanceof Workspace typedWorkspace) {
            return typedWorkspace.root();
        }
        if (workspace instanceof Path path) {
            return path;
        }
        if (workspace instanceof CharSequence text && !text.toString().isBlank()) {
            return Path.of(text.toString());
        }
        return null;
    }

    public Object getContextUsage(String sessionId, String contextId) {
        return Map.of("session_id", sessionId, "context_id", contextId);
    }

    public Object getContextOccupancy(String sessionId, String contextId) {
        return Map.of("session_id", sessionId, "context_id", contextId, "occupancy", 0);
    }

    public Object getCurrentContext(String sessionId, String contextId) {
        return Map.of("session_id", sessionId, "context_id", contextId);
    }

    private void loadRails(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        if (!hasItems(resources.getRails())) {
            return;
        }
        List<DeepAgentRail> resolvedRails = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> railClass : RuntimeExtensionLoader.loadRuntimeRails(runtimeExt, runtimeExtensionSessionId())) {
                resolvedRails.add(instantiateRail(railClass));
            }
        } else {
            resolvedRails.addAll(HarnessConfigBuilder.resolveRails(resources));
        }
        for (DeepAgentRail rail : resolvedRails) {
            registerRail(rail).join();
            loaded.add("rail:" + rail.getClass().getSimpleName());
        }
    }

    private void loadTools(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        if (!hasItems(resources.getTools())) {
            return;
        }
        List<Tool> resolvedTools = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> toolClass : RuntimeExtensionLoader.loadRuntimeTools(runtimeExt, runtimeExtensionSessionId())) {
                resolvedTools.add(instantiateTool(toolClass));
            }
        } else {
            resolvedTools.addAll(HarnessConfigBuilder.resolveTools(resources));
        }
        for (Tool tool : resolvedTools) {
            registerTool(tool);
            loaded.add("tool:" + tool.getClass().getSimpleName());
        }
    }

    private void loadSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> loaded
    ) {
        List<String> skillDirs = resolveSkillDirs(configPath, resources, runtimeExt);
        if (skillDirs.isEmpty()) {
            return;
        }
        SkillUseRail existingRail = findFirstSkillUseRail();
        if (existingRail != null) {
            existingRail.prependSkillDirs(skillDirs);
            existingRail.reloadSkills();
        } else {
            String mode = resources.getSkills().getMode() == null ? SkillUseRail.SKILL_MODE_ALL : resources.getSkills().getMode();
            SkillUseRail newRail = new SkillUseRail(
                    String.join(",", skillDirs),
                    mode,
                    false,
                    true,
                    null,
                    null
            );
            registerRail(newRail).join();
            newRail.reloadSkills();
        }
        skillDirs.forEach(skillDir -> loaded.add("skill_dir:" + skillDir));
    }

    private void unloadRails(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        if (!hasItems(resources.getRails())) {
            return;
        }
        Set<Class<?>> railTypes = new LinkedHashSet<>();
        if (runtimeExt != null) {
            railTypes.addAll(RuntimeExtensionLoader.loadRuntimeRails(runtimeExt, runtimeExtensionSessionId()));
        } else {
            for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
                Class<?> railType = classFromSpec(spec.getModule(), spec.getClassName());
                if (railType != null) {
                    railTypes.add(railType);
                }
            }
        }
        for (DeepAgentRail rail : getRails()) {
            if (railTypes.contains(rail.getClass())) {
                unregisterRail(rail).join();
                unloaded.add("rail:" + rail.getClass().getSimpleName());
            }
        }
    }

    private void unloadTools(
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        if (!hasItems(resources.getTools())) {
            return;
        }
        List<Tool> resolvedTools = new ArrayList<>();
        if (runtimeExt != null) {
            for (Class<?> toolClass : RuntimeExtensionLoader.loadRuntimeTools(runtimeExt, runtimeExtensionSessionId())) {
                resolvedTools.add(instantiateTool(toolClass));
            }
        } else {
            resolvedTools.addAll(HarnessConfigBuilder.resolveTools(resources));
        }
        for (Tool tool : resolvedTools) {
            if (tool.getCard() == null) {
                continue;
            }
            unloaded.add("tool_id:" + tool.getCard().getId());
            unregisterTool(tool.getCard().getName());
            unloaded.add("tool:" + tool.getCard().getName());
        }
    }

    private void unloadSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt,
            List<String> unloaded
    ) {
        List<String> skillDirs = resolveSkillDirs(configPath, resources, runtimeExt);
        if (skillDirs.isEmpty()) {
            return;
        }
        SkillUseRail existingRail = findFirstSkillUseRail();
        if (existingRail != null) {
            existingRail.removeSkillDirs(skillDirs);
            existingRail.reloadSkills();
        }
        skillDirs.forEach(skillDir -> unloaded.add("skill_dir:" + skillDir));
    }

    private List<String> resolveSkillDirs(
            Path configPath,
            HarnessConfig.ResourcesSchema resources,
            RuntimeExtensionArtifact runtimeExt
    ) {
        if (resources.getSkills() == null || resources.getSkills().getDirs() == null
                || resources.getSkills().getDirs().isEmpty()) {
            return List.of();
        }
        if (runtimeExt != null) {
            return RuntimeExtensionLoader.loadRuntimeSkillDirs(runtimeExt);
        }
        Path sourceDir = configPath.getParent();
        return resources.getSkills().getDirs().stream()
                .map(dir -> sourceDir.resolve(dir).toAbsolutePath().normalize().toString())
                .toList();
    }

    private SkillUseRail findFirstSkillUseRail() {
        return rails.stream()
                .filter(SkillUseRail.class::isInstance)
                .map(SkillUseRail.class::cast)
                .findFirst()
                .orElse(null);
    }

    private RuntimeExtensionArtifact runtimeExtensionArtifactForConfig(
            Path configPath,
            HarnessConfig.ResourcesSchema resources
    ) {
        String extensionName = configPath.getParent().getFileName().toString();
        String prefix = "openjiuwen.extensions.harness." + extensionName;
        boolean hasRuntimeModule = false;
        if (resources.getRails() != null) {
            for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
                if (isRuntimePackageSpec(spec.getType(), spec.getModule(), prefix)) {
                    hasRuntimeModule = true;
                    break;
                }
            }
        }
        if (!hasRuntimeModule && resources.getTools() != null) {
            for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
                if (isRuntimePackageSpec(spec.getType(), spec.getModule(), prefix)) {
                    hasRuntimeModule = true;
                    break;
                }
            }
        }
        if (!hasRuntimeModule) {
            return null;
        }
        return RuntimeExtensionArtifact.builder()
                .extensionName(extensionName)
                .runtimePath(configPath.getParent().toString())
                .configPath(configPath.toString())
                .build();
    }

    private String runtimeExtensionSessionId() {
        if (card.getId() != null && !card.getId().isBlank()) {
            return card.getId();
        }
        if (card.getName() != null && !card.getName().isBlank()) {
            return card.getName();
        }
        return "deep_agent";
    }

    private static HarnessConfig.ResourcesSchema resourcesOf(ResolvedHarnessConfig resolved) {
        return resolved == null || resolved.getConfig() == null ? null : resolved.getConfig().getResources();
    }

    private static boolean isRuntimePackageSpec(String type, String module, String prefix) {
        return "package".equals(type) && module != null
                && (module.equals(prefix) || module.startsWith(prefix + "."));
    }

    private static boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private static DeepAgentRail instantiateRail(Class<?> railClass) {
        Object instance = instantiate(railClass);
        if (instance instanceof DeepAgentRail rail) {
            return rail;
        }
        throw new IllegalArgumentException("Runtime rail is not a DeepAgentRail: " + railClass.getName());
    }

    private static Tool instantiateTool(Class<?> toolClass) {
        Object instance = instantiate(toolClass);
        if (instance instanceof Tool tool) {
            return tool;
        }
        throw new IllegalArgumentException("Runtime tool is not a Tool: " + toolClass.getName());
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Failed to instantiate runtime resource: " + type.getName(), exception);
        }
    }

    private static Class<?> classFromSpec(String module, String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        List<String> candidates = className.contains(".") || className.contains("$")
                ? List.of(className)
                : List.of(module == null || module.isBlank() ? className : module + "." + className, className);
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private void initialConfigure(DeepAgentConfig newConfig) {
        config = newConfig;
        syncConfig();
        initialized = true;
    }

    private void hotReconfigure(DeepAgentConfig newConfig) {
        config = newConfig;
        rails.forEach(rail -> rail.uninit(this));
        rails.clear();
        tools.clear();
        subagents.clear();
        syncConfig();
    }

    private void syncConfig() {
        if (config.getTools() != null) {
            config.getTools().forEach(this::registerTool);
        }
        if (config.getRails() != null) {
            config.getRails().forEach(this::addRail);
        }
        if (config.getSubagents() != null) {
            subagents.putAll(config.getSubagents());
        }
        addDefaultRailsFromConfig();
    }

    private void addDefaultRailsFromConfig() {
        if (config.isEnablePlanMode() && findRailsByType(TaskPlanningRail.class).isEmpty()) {
            addRail(new TaskPlanningRail(false, 20, config.getModelSelection()));
        }
        if ((config.isEnableSkillDiscovery() || hasConfiguredSkills())
                && findRailsByType(SkillUseRail.class).isEmpty()) {
            addRail(new SkillUseRail(defaultSkillsDir()));
        }
    }

    private boolean hasConfiguredSkills() {
        Object skills = config.getSkills();
        if (skills == null) {
            return false;
        }
        if (skills instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (skills instanceof Iterable<?> values) {
            return values.iterator().hasNext();
        }
        if (skills instanceof Map<?, ?> values) {
            return !values.isEmpty();
        }
        return true;
    }

    private String defaultSkillsDir() {
        Object workspace = config.getWorkspace();
        if (workspace instanceof Workspace workspaceObj) {
            Path skillsPath = workspaceObj.getNodePath("skills");
            return skillsPath == null ? workspaceObj.resolve("skills").toString() : skillsPath.toString();
        }
        if (workspace instanceof Path path) {
            return path.resolve("skills").toString();
        }
        if (workspace instanceof CharSequence text && !text.toString().isBlank()) {
            return Path.of(text.toString()).resolve("skills").toString();
        }
        return Path.of("skills").toString();
    }

    private boolean hasRemainingTasks(AgentSessionApi session) {
        DeepAgentState state = loadState(session);
        return state.getTaskPlan() != null && state.getTaskPlan().getNextTask() != null;
    }

    private static Map<String, Object> invokeReactAgent(
            Object reactAgent,
            Map<String, Object> inputs,
            AgentSessionApi session
    ) {
        Object result = invokeFirstMatching(
                reactAgent,
                List.of(
                        new Object[]{inputs, session, Boolean.TRUE},
                        new Object[]{inputs, session},
                        new Object[]{inputs}
                )
        );
        if (result instanceof CompletionStage<?> stage) {
            result = stage.toCompletableFuture().join();
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("output", result == null ? "" : String.valueOf(result));
        return normalized;
    }

    private static Object invokeFirstMatching(Object target, List<Object[]> argumentOptions) {
        for (Object[] arguments : argumentOptions) {
            Method method = findInvokeMethod(target.getClass(), arguments);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }
        throw new IllegalStateException("react_agent.invoke is not available");
    }

    private static Method findInvokeMethod(Class<?> type, Object[] arguments) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"invoke".equals(method.getName()) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                if (parametersAccept(method.getParameterTypes(), arguments)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean parametersAccept(Class<?>[] parameterTypes, Object[] arguments) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (arguments[index] == null) {
                continue;
            }
            Class<?> parameterType = parameterTypes[index].isPrimitive()
                    ? primitiveWrapper(parameterTypes[index])
                    : parameterTypes[index];
            if (!parameterType.isInstance(arguments[index])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> primitiveWrapper(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
