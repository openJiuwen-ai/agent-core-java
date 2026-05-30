/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.workspace.Workspace;

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

    private DeepAgentConfig config;
    private ReActAgent delegate;

    public DeepAgent(AgentCard card) {
        super(card);
        this.config = new DeepAgentConfig();
        this.config.setCard(card);
        this.delegate = new ReActAgent(card);
    }

    @Override
    public BaseAgent configure(Object configObj) {
        if (!(configObj instanceof DeepAgentConfig deepAgentConfig)) {
            throw new IllegalArgumentException("Expected DeepAgentConfig, got: "
                    + (configObj != null ? configObj.getClass().getName() : "null"));
        }
        this.config = deepAgentConfig;
        this.delegate = new ReActAgent(deepAgentConfig.getCard() != null ? deepAgentConfig.getCard() : getCard());
        this.delegate.configure(toReActConfig(deepAgentConfig));
        if (deepAgentConfig.getTools() != null && !deepAgentConfig.getTools().isEmpty()) {
            this.delegate.getAbilityManager().add(deepAgentConfig.getTools());
        }
        if (deepAgentConfig.getRails() != null) {
            for (var rail : deepAgentConfig.getRails()) {
                if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepRail) {
                    deepRail.setWorkspace(deepAgentConfig.getWorkspace());
                }
                this.delegate.registerRail(rail);
                if (rail instanceof com.openjiuwen.harness.rails.DeepAgentRail deepRail) {
                    deepRail.init(this);
                }
            }
        }
        return this;
    }

    @Override
    public Object getConfig() {
        return config;
    }

    public Workspace getWorkspace() {
        return config.getWorkspace();
    }

    public ReActAgent getDelegate() {
        return delegate;
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        return delegate.invoke(normalizeInputs(inputs), session);
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return delegate.stream(normalizeInputs(inputs), session, streamModes);
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

        DeepAgent subagent = findConfiguredSubagent(subagentType);
        if (subagent == null) {
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
}
