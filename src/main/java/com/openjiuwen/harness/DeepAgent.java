/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
     * Placeholder implementation - full callback system deferred.
     */
    public void fireCallback(String eventName, Map<String, Object> data) {
        // Placeholder - actual implementation would notify registered rails
    }

    /**
     * Cancel a running task.
     * Placeholder implementation - full task management deferred.
     */
    public void cancelTask(String taskId) {
        // Placeholder - actual implementation would cancel task via task manager
    }

    /**
     * Spawn a subagent task.
     * Placeholder implementation - full subagent spawning deferred.
     */
    public void spawnSubagentTask(String taskId, String subagentType, String description, String subSessionId) {
        // Placeholder - actual implementation would spawn subagent via task loop
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
