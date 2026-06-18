/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
        }
    }

    public void unregisterTool(String toolName) {
        tools.remove(toolName);
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
        agent.configure(spec.getConfig() == null ? config : spec.getConfig());
        return agent;
    }

    public Map<String, Object> normalizeInputs(Map<String, Object> inputs) {
        return inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }

    public CompletableFuture<Map<String, Object>> invoke(Map<String, Object> inputs) {
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
            result.put("mode", loadState(null).getPlanMode().getMode());
            for (DeepAgentRail rail : rails.reversed()) {
                rail.afterInvoke(context);
            }
            return CompletableFuture.completedFuture(result);
        } finally {
            invokeActive = false;
        }
    }

    public Iterator<Map<String, Object>> stream(Map<String, Object> inputs) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("type", "deep_agent_chunk");
        chunk.put("input", normalizeInputs(inputs));
        chunk.put("final", true);
        return List.of(chunk).iterator();
    }

    public CompletableFuture<Map<String, Object>> followUp(String message, String taskId, Object session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "follow_up");
        result.put("message", message);
        result.put("task_id", taskId);
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<Map<String, Object>> steer(String message, Object session) {
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
        return DeepAgentState.fromSessionMap(null);
    }

    public void saveState(Object session, DeepAgentState state) {
        Objects.requireNonNullElseGet(state, DeepAgentState::new).toSessionMap();
    }

    public void clearState(Object session, boolean clearPersisted) {
        loopCoordinator.reset();
    }

    public void switchMode(Object session, AgentMode mode) {
        DeepAgentState state = loadState(session);
        state.getPlanMode().setMode(mode == null ? AgentMode.NORMAL.value() : mode.value());
        saveState(session, state);
    }

    public String getPlanFilePath(Object session) {
        DeepAgentState state = loadState(session);
        return state.getPlanMode().getPlanSlug();
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
    }
}
