/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.prompts.DeepAgentPromptBuilder;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.workspace.DirectoryBuilder;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Field;

/**
 * First Java harness agent built on top of the existing {@link ReActAgent}.
 *
 * <p>Mirrors Python's {@code DeepAgent} in
 * {@code openjiuwen.harness.deep_agent}.
 *
 * <p>This is a minimal runtime-level port: it owns a workspace-aware config
 * surface and delegates model/tool execution to the already-maintained Java
 * single-agent runtime.
 */
public class DeepAgent extends BaseAgent {

    private static final String SESSION_STATE_KEY = "deepagent";

    private DeepAgentConfig config;
    private ReActAgentConfig reactConfig;
    private ReActAgent delegate;
    private DeepAgentPromptBuilder systemPromptBuilder;
    private boolean workspaceInitialized;

    public DeepAgent(AgentCard card) {
        super(card);
        this.config = new DeepAgentConfig();
        this.config.setCard(card);
        this.reactConfig = new ReActAgentConfig();
        this.delegate = new ReActAgent(card);
        this.systemPromptBuilder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);
        this.workspaceInitialized = false;
    }

    @Override
    public BaseAgent configure(Object configObj) {
        if (!(configObj instanceof DeepAgentConfig deepAgentConfig)) {
            throw new IllegalArgumentException("Expected DeepAgentConfig, got: "
                    + (configObj != null ? configObj.getClass().getName() : "null"));
        }
        this.config = deepAgentConfig;
        this.reactConfig = toReActConfig(deepAgentConfig);
        this.delegate = new ReActAgent(deepAgentConfig.getCard() != null ? deepAgentConfig.getCard() : getCard());
        this.delegate.configure(reactConfig);
        if (deepAgentConfig.getModel() != null) {
            this.delegate.setLlm(deepAgentConfig.getModel());
        }
        String language = deepAgentConfig.getWorkspace() != null ? deepAgentConfig.getWorkspace().getLanguage() : "cn";
        this.systemPromptBuilder = new DeepAgentPromptBuilder(language, DeepAgentPromptBuilder.PromptMode.FULL);
        if (deepAgentConfig.getTools() != null && !deepAgentConfig.getTools().isEmpty()) {
            this.delegate.getAbilityManager().add(deepAgentConfig.getTools());
        }
        if (deepAgentConfig.getRails() != null) {
            for (var rail : deepAgentConfig.getRails()) {
                if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepRail) {
                    deepRail.setWorkspace(deepAgentConfig.getWorkspace());
                    deepRail.setSysOperation(deepAgentConfig.getSysOperation());
                }
                this.delegate.registerRail(rail);
                if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepRail) {
                    deepRail.init(this);
                }
            }
        }
        this.workspaceInitialized = false;
        return this;
    }

    @Override
    public Object getConfig() {
        return config;
    }

    public Workspace getWorkspace() {
        return config.getWorkspace();
    }

    public DeepAgentConfig getDeepConfig() {
        return config;
    }

    public ReActAgent getDelegate() {
        return delegate;
    }

    @Override
    public AbilityManager getAbilityManager() {
        return delegate != null ? delegate.getAbilityManager() : super.getAbilityManager();
    }

    public DeepAgentPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public ReActAgentConfig getReactConfig() {
        return reactConfig;
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        ensureInitialized();
        return delegate.invoke(normalizeInputs(inputs), session);
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        ensureInitialized();
        return delegate.stream(normalizeInputs(inputs), session, streamModes);
    }

    public void initWorkspace() {
        if (config == null || config.getWorkspace() == null || config.getSysOperation() == null) {
            return;
        }
        DirectoryBuilder builder = new DirectoryBuilder(
                config.getSysOperation(),
                config.getWorkspace().getRootPath());
        builder.build(config.getWorkspace().getDirectories());
        workspaceInitialized = true;
    }

    public void ensureInitialized() {
        if (workspaceInitialized) {
            return;
        }
        if (config == null || !config.getAutoCreateWorkspace() || config.getSysOperation() == null) {
            return;
        }
        initWorkspace();
    }

    private static Object normalizeInputs(Object inputs) {
        if (inputs instanceof String text) {
            return Map.of("query", text);
        }
        return inputs;
    }

    private static ReActAgentConfig toReActConfig(DeepAgentConfig config) {
        ReActAgentConfig reactConfig = new ReActAgentConfig();
        reactConfig.configureMaxIterations(config.getMaxIterations());
        assignField(reactConfig, "modelClientConfig", config.getModelClientConfig());
        assignField(reactConfig, "modelConfigObj", config.getModelRequestConfig());
        assignField(reactConfig, "sysOperationId", config.getSysOperationId());
        String modelName = readStringField(config.getModelRequestConfig(), "modelName");
        if (modelName != null && !modelName.isBlank()) {
            reactConfig.configureModel(modelName);
        }
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            reactConfig.configurePromptTemplate(List.of(Map.of(
                    "role", "system",
                    "content", config.getSystemPrompt()
            )));
        }
        return reactConfig;
    }

    private static void assignField(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set field '" + fieldName + "'", e);
            }
        }
        throw new IllegalStateException("Field not found: " + fieldName + " on " + target.getClass().getName());
    }

    private static String readStringField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return value != null ? String.valueOf(value) : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    /**
     * Fire a callback event on the agent.
     */
    public void fireCallback(String eventName, Map<String, Object> data) {
        AgentCallbackEvent event = resolveCallbackEvent(eventName);
        Map<String, Object> extra = new LinkedHashMap<>(data != null ? data : Map.of());
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(this)
                .event(event)
                .config(config)
                .extra(extra)
                .build();
        if (event == AgentCallbackEvent.BEFORE_TASK_ITERATION
                || event == AgentCallbackEvent.AFTER_TASK_ITERATION) {
            ctx.setInputs(TaskIterationInputs.from(extra));
        }
        fireCallbackEvent(event, ctx);
    }

    /**
     * Cancel a running task.
     */
    public void cancelTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        if (config != null && config.getSessionToolkit() != null) {
            config.getSessionToolkit().cancelTask(taskId);
        }
    }

    /**
     * Spawn a subagent task.
     */
    public void spawnSubagentTask(String taskId, String subagentType, String description, String subSessionId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        String effectiveSubSessionId = subSessionId != null && !subSessionId.isBlank() ? subSessionId : taskId;
        String effectiveDescription = description != null ? description : "";
        if (config != null && config.getSessionToolkit() != null) {
            config.getSessionToolkit().upsertTask(
                    taskId,
                    effectiveSubSessionId,
                    effectiveDescription,
                    "running"
            );
        }

        DeepAgent subagent;
        try {
            subagent = createSubagent(subagentType, effectiveSubSessionId);
        } catch (IllegalArgumentException e) {
            return;
        }
        DeepAgentConfig.SessionToolkit toolkit = config != null ? config.getSessionToolkit() : null;
        CompletableFuture.runAsync(() -> {
            try {
                Object result = subagent.invoke(
                        Map.of("query", effectiveDescription, "conversation_id", effectiveSubSessionId),
                        new AgentTeamSession(effectiveSubSessionId, safeCardValue(subagent.getCard(), "name"))
                );
                if (toolkit != null) {
                    toolkit.completeTask(taskId, extractOutput(result));
                }
            } catch (Exception e) {
                if (toolkit != null) {
                    toolkit.failTask(taskId, e.getMessage());
                }
            }
        });
    }

    /**
     * Create or resolve a subagent for delegated task execution.
     *
     * <p>Explicitly configured subagents win. When enabled, the implicit
     * {@code general-purpose} subagent inherits the parent's tools, MCPs,
     * skills, model, workspace, and sys-operation context.</p>
     */
    public DeepAgent createSubagent(String subagentType, String sessionId) {
        DeepAgent explicit = findConfiguredSubagent(subagentType);
        if (explicit != null) {
            return explicit;
        }
        String requested = subagentType != null ? subagentType : "";
        if (!"general-purpose".equals(requested)
                || config == null
                || !config.getAddGeneralPurposeAgent()) {
            throw new IllegalArgumentException("Subagent not found: " + requested);
        }

        AgentCard card = AgentCard.builder()
                .name("general-purpose")
                .description("General-purpose subagent")
                .id(sessionId != null && !sessionId.isBlank() ? sessionId : "general-purpose")
                .build();
        DeepAgentConfig subConfig = new DeepAgentConfig();
        subConfig.setCard(card);
        subConfig.setSystemPrompt(config.getSystemPrompt());
        subConfig.setModel(config.getModel());
        subConfig.setModelClientConfig(config.getModelClientConfig());
        subConfig.setModelRequestConfig(config.getModelRequestConfig());
        subConfig.setSysOperation(config.getSysOperation());
        subConfig.setSysOperationId(config.getSysOperationId());
        subConfig.setWorkspace(config.getWorkspace());
        subConfig.setTools(config.getTools());
        subConfig.setMcps(config.getMcps());
        subConfig.setSkills(config.getSkills());
        subConfig.setMaxIterations(config.getMaxIterations());

        DeepAgent subagent = new DeepAgent(card);
        subagent.configure(subConfig);
        return subagent;
    }

    private static AgentCallbackEvent resolveCallbackEvent(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            if (event.getValue().equals(eventName) || event.name().equalsIgnoreCase(eventName)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unsupported callback event: " + eventName);
    }

    private DeepAgent findConfiguredSubagent(String subagentType) {
        if (config == null || config.getSubagents() == null || config.getSubagents().isEmpty()) {
            return null;
        }
        String requested = subagentType != null ? subagentType : "";
        if (requested.isBlank() && config.getSubagents().size() == 1) {
            return config.getSubagents().get(0);
        }
        for (DeepAgent subagent : config.getSubagents()) {
            AgentCard card = subagent.getCard();
            String name = safeCardValue(card, "name");
            String id = safeCardValue(card, "id");
            if (requested.equals(name) || requested.equals(id)) {
                return subagent;
            }
        }
        return null;
    }

    private static String safeCardValue(Object card, String fieldName) {
        String value = readStringField(card, fieldName);
        return value != null ? value : "";
    }

    private static String extractOutput(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object output = map.get("output");
            if (output != null) {
                return String.valueOf(output);
            }
        }
        return result != null ? String.valueOf(result) : "";
    }
    
    private String currentMode = "normal";
    private String planSlug = null;
    
    /**
     * Get the current agent mode.
     */
    public String getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Set the agent mode.
     */
    public void setCurrentMode(String mode) {
        this.currentMode = mode != null ? mode : "normal";
    }
    
    /**
     * Get the plan slug.
     */
    public String getPlanSlug() {
        return planSlug;
    }
    
    /**
     * Set the plan slug.
     */
    public void setPlanSlug(String slug) {
        this.planSlug = slug;
    }

    /**
     * Load session-scoped DeepAgent state.
     */
    @SuppressWarnings("unchecked")
    public DeepAgentState loadState(Session session) {
        if (session == null) {
            DeepAgentState state = new DeepAgentState();
            state.getPlanMode().setMode(currentMode);
            state.getPlanMode().setPlanSlug(planSlug);
            return state;
        }
        Object raw = session.getState(SESSION_STATE_KEY);
        if (raw instanceof DeepAgentState state) {
            return state;
        }
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    data.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return DeepAgentState.fromSessionMap(data);
        }
        return new DeepAgentState();
    }

    /**
     * Persist session-scoped DeepAgent state.
     */
    public void saveState(Session session, DeepAgentState state) {
        if (session == null || state == null) {
            return;
        }
        session.updateState(Map.of(SESSION_STATE_KEY, state.toSessionMap()));
        currentMode = state.getPlanMode().getMode();
        planSlug = state.getPlanMode().getPlanSlug();
    }

    public void clearState(Session session) {
        clearState(session, false);
    }

    public void clearState(Session session, boolean clearPersisted) {
        if (session == null || !clearPersisted) {
            return;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(SESSION_STATE_KEY, null);
        session.updateState(update);
    }

    /**
     * Switch agent mode for the current session.
     */
    public void switchMode(Session session, String mode) {
        DeepAgentState state = loadState(session);
        String target = mode != null && !mode.isBlank() ? mode : "normal";
        String previous = state.getPlanMode().getMode();
        state.getPlanMode().setPrePlanMode(previous);
        state.getPlanMode().setMode(target);
        saveState(session, state);
    }

    /**
     * Restore the mode active before plan mode.
     */
    public void restoreModeAfterPlanExit(Session session) {
        DeepAgentState state = loadState(session);
        String previous = state.getPlanMode().getPrePlanMode();
        state.getPlanMode().setMode(previous != null && !previous.isBlank() ? previous : "normal");
        state.getPlanMode().setPrePlanMode(null);
        saveState(session, state);
    }

    /**
     * Resolve the current plan file path from session state.
     */
    public Path getPlanFilePath(Session session) {
        DeepAgentState state = loadState(session);
        String slug = state.getPlanMode().getPlanSlug();
        if (slug == null || slug.isBlank() || config == null || config.getWorkspace() == null) {
            return null;
        }
        return com.openjiuwen.harness.tools.agent_control.AgentModeTools.resolvePlanFilePath(
                config.getWorkspace().getRootPath(),
                slug
        );
    }

    /**
     * Publish steering text to a running task loop.
     */
    public void steer(String msg, Session session) {
        if (delegate != null) {
            delegate.pushSteering(session != null ? session.getSessionId() : null, msg);
        }
    }
}
