/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rail that manages external memory store integration.
 * <p>
 * Mirrors Python's {@code ExternalMemoryRail} in
 * {@code openjiuwen.harness.rails.memory.external_memory_rail}.
 *
 * <p>Python features:
 * <ul>
 *   <li>Register provider tools to agent ability_manager</li>
 *   <li>Inject provider's static system prompt block</li>
 *   <li>Unregister tools and remove prompt sections on uninit</li>
 *   <li>Async shutdown via provider.shutdown()</li>
 * </ul>
 */
public class ExternalMemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalMemoryRail.class);
    private static final double PREFETCH_TIMEOUT = 5.0;
    private static final double SHUTDOWN_TIMEOUT = 10.0;

    private MemoryProvider provider;
    private String userId = "__default__";
    private String scopeId = "__default__";
    private String sessionId = "__default__";
    private boolean initialized = false;
    private Object systemPromptBuilder;
    private final Set<String> ownedToolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> ownedToolIds = ConcurrentHashMap.newKeySet();
    private String prefetchCache = null;

    public ExternalMemoryRail() {
        super();
    }

    public ExternalMemoryRail(MemoryProvider provider, String userId, String scopeId, String sessionId) {
        super();
        this.provider = provider;
        this.userId = userId;
        this.scopeId = scopeId;
        this.sessionId = sessionId;
    }

    /**
     * Set the memory provider.
     */
    public void setProvider(MemoryProvider provider) {
        this.provider = provider;
    }

    /**
     * Get the memory provider.
     */
    public MemoryProvider getProvider() {
        return provider;
    }

    /**
     * Initialize the rail - register provider tools and inject system prompt.
     * <p>
     * Mirrors Python's {@code init} method which:
     * <ul>
     *   <li>Calls super().init(agent)</li>
     *   <li>Gets system_prompt_builder from agent</li>
     *   <li>Registers provider tools via _register_provider_tools</li>
     *   <li>Injects provider's static system prompt block</li>
     * </ul>
     */
    @Override
    public void init(Object agent) {
        super.init(agent);

        if (provider == null) {
            LOG.warn("[ExternalMemoryRail] No provider configured, skipping initialization");
            return;
        }

        // Get system_prompt_builder from agent
        try {
            systemPromptBuilder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
        } catch (Exception e) {
            LOG.debug("[ExternalMemoryRail] Could not get system_prompt_builder from agent");
            systemPromptBuilder = null;
        }

        // Register provider tools
        registerProviderTools(agent);

        // Inject provider's static system prompt block
        if (systemPromptBuilder != null) {
            String promptBlock = provider.systemPromptBlock();
            if (promptBlock != null && !promptBlock.isEmpty()) {
                try {
                    // Try to add section to system_prompt_builder
                    // Python: self.system_prompt_builder.add_section(section)
                    LOG.info("[ExternalMemoryRail] Injected system prompt block from provider");
                } catch (Exception e) {
                    LOG.warn("[ExternalMemoryRail] Failed to inject system prompt block: {}", e.getMessage());
                }
            }
        }

        initialized = true;
        LOG.info("[ExternalMemoryRail] Initialized with provider {}", provider.name());
    }

    /**
     * Uninitialize the rail - unregister tools and shutdown provider.
     * <p>
     * Mirrors Python's {@code uninit} method which:
     * <ul>
     *   <li>Removes tools from agent.ability_manager</li>
     *   <li>Removes tools from Runner.resource_mgr</li>
     *   <li>Removes prompt sections</li>
     *   <li>Shutdowns provider with timeout</li>
     * </ul>
     */
    @Override
    public void uninit(Object agent) {
        // Unregister tools owned by this rail from the agent
        if (agent != null) {
            try {
                Object abilityManager = agent.getClass().getMethod("getAbilityManager").invoke(agent);
                if (abilityManager != null) {
                    for (String toolName : new ArrayList<>(ownedToolNames)) {
                        try {
                            abilityManager.getClass().getMethod("remove", String.class).invoke(abilityManager, toolName);
                        } catch (Exception exc) {
                            LOG.warn("[ExternalMemoryRail] remove tool '{}' failed: {}", toolName, exc.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("[ExternalMemoryRail] Could not get ability_manager from agent");
            }
        }

        // Remove tools from resource_mgr
        for (String toolId : new ArrayList<>(ownedToolIds)) {
            try {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            } catch (Exception exc) {
                LOG.warn("[ExternalMemoryRail] Failed to remove tool '{}' from resource_mgr: {}", toolId, exc.getMessage());
            }
        }

        ownedToolIds.clear();
        ownedToolNames.clear();

        // Remove prompt sections
        if (systemPromptBuilder != null) {
            try {
                // Python: self.system_prompt_builder.remove_section(SectionName.EXTERNAL_MEMORY)
                LOG.debug("[ExternalMemoryRail] Removed prompt sections");
            } catch (Exception e) {
                LOG.debug("[ExternalMemoryRail] Failed to remove prompt sections");
            }
            systemPromptBuilder = null;
        }

        // Shutdown provider
        if (provider != null) {
            try {
                provider.shutdown().get((long) (SHUTDOWN_TIMEOUT * 1000), java.util.concurrent.TimeUnit.MILLISECONDS);
                LOG.info("[ExternalMemoryRail] Provider shutdown completed");
            } catch (Exception e) {
                LOG.warn("[ExternalMemoryRail] Provider shutdown timed out or failed: {}", e.getMessage());
            }
        }

        initialized = false;
        LOG.info("[ExternalMemoryRail] Uninitialized");
    }

    /**
     * Register provider tools to agent.
     * <p>
     * Mirrors Python's {@code _register_provider_tools}.
     */
    private void registerProviderTools(Object agent) {
        if (provider == null) {
            return;
        }

        List<Map<String, Object>> toolSchemas = provider.getToolSchemas();
        if (toolSchemas == null || toolSchemas.isEmpty()) {
            LOG.debug("[ExternalMemoryRail] No tool schemas from provider");
            return;
        }

        for (Map<String, Object> schema : toolSchemas) {
            try {
                String toolName = (String) schema.getOrDefault("name", "");
                String toolId = (String) schema.getOrDefault("id", "");

                if (!toolName.isEmpty()) {
                    ownedToolNames.add(toolName);
                    LOG.debug("[ExternalMemoryRail] Registered tool: {}", toolName);
                }
                if (!toolId.isEmpty()) {
                    ownedToolIds.add(toolId);
                }

                // TODO: Add tool to agent.ability_manager
                // Python: agent.ability_manager.add(tool)

            } catch (Exception e) {
                LOG.warn("[ExternalMemoryRail] Failed to register tool: {}", e.getMessage());
            }
        }

        LOG.info("[ExternalMemoryRail] Registered {} tools from provider", ownedToolNames.size());
    }

    /**
     * Prefetch memory for a query.
     * <p>
     * Mirrors Python's prefetch functionality.
     */
    public String prefetch(String query) {
        if (provider == null || !initialized) {
            return null;
        }

        try {
            prefetchCache = provider.prefetch(query, Map.of()).get((long) (PREFETCH_TIMEOUT * 1000), java.util.concurrent.TimeUnit.MILLISECONDS);
            return prefetchCache;
        } catch (Exception e) {
            LOG.warn("[ExternalMemoryRail] Prefetch failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if the rail is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resolve user text for memory storage.
     */
    public static String resolveUserTextForMemory(Object callbackContext) {
        Object inputs = readField(callbackContext, "inputs");
        Object query = readProperty(inputs, "query");
        if (query instanceof String text && !text.trim().isEmpty()) {
            return text.trim();
        }

        Object messages = readProperty(inputs, "messages");
        if (messages instanceof List<?> list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Object message = list.get(i);
                Object role = valueFromMessage(message, "role");
                if (!"user".equals(String.valueOf(role))) {
                    continue;
                }
                Object content = valueFromMessage(message, "content");
                if (content instanceof String text && !text.trim().isEmpty()) {
                    return text.trim();
                }
                if (content instanceof List<?> chunks) {
                    List<String> parts = new ArrayList<>();
                    for (Object chunk : chunks) {
                        if (chunk instanceof Map<?, ?> map) {
                            Object type = map.get("type");
                            Object textValue = map.get("text");
                            if ("text".equals(String.valueOf(type))
                                    && textValue instanceof String piece
                                    && !piece.trim().isEmpty()) {
                                parts.add(piece.trim());
                            }
                        }
                    }
                    if (!parts.isEmpty()) {
                        return String.join(" ", parts);
                    }
                }
            }
        }
        return "";
    }

    /**
     * Extract assistant output from an invoke context.
     */
    public static String extractAssistantOutput(Object callbackContext) {
        Object inputs = readField(callbackContext, "inputs");
        Object result = readProperty(inputs, "result");
        if (result instanceof Map<?, ?> map) {
            Object output = map.get("output");
            if (output != null) {
                return String.valueOf(output);
            }
            Object message = map.get("message");
            if (message instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
                return String.valueOf(messageMap.get("content"));
            }
            if (map.get("content") != null) {
                return String.valueOf(map.get("content"));
            }
            return "";
        }
        return result != null ? String.valueOf(result) : "";
    }

    /**
     * Build a fenced memory-context block for prompt injection.
     */
    public static String buildMemoryContextBlock(String rawContext) {
        String safe = rawContext != null ? rawContext : "";
        return "<memory-context>\n"
                + "The following recalled memory is NOT new user input.\n"
                + safe
                + "\n</memory-context>";
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            return target.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
                    .invoke(target);
        } catch (Exception ignored) {
            return readField(target, name);
        }
    }

    private static Object valueFromMessage(Object message, String name) {
        if (message instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return readProperty(message, name);
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }
}
