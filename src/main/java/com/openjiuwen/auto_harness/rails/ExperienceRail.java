/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.tools.ExperienceSearchTool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Experience rail for auto-harness agents.
 * <p>
 * Mirrors Python's {@code AutoHarnessExperienceRail} in
 * {@code openjiuwen.auto_harness.rails.experience_rail}.
 *
 * <p>Python features:
 * <ul>
 *   <li>Register experience_search tool to agent ability_manager</li>
 *   <li>Add tool to Runner.resource_mgr</li>
 *   <li>Inject experience prompt section</li>
 *   <li>Unregister tools and remove prompt sections on uninit</li>
 * </ul>
 */
public class ExperienceRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ExperienceRail.class);

    private final String experienceDir;
    private final String language;
    private final Set<String> ownedToolNames = new HashSet<>();
    private final Set<String> ownedToolIds = new HashSet<>();
    private Object systemPromptBuilder;

    public ExperienceRail(String experienceDir) {
        this(experienceDir, "cn");
    }

    public ExperienceRail(String experienceDir, String language) {
        this.experienceDir = experienceDir;
        this.language = language;
        setPriority(80);
    }

    /**
     * Initialize with agent - register experience tool and inject prompt section.
     * <p>
     * Mirrors Python's {@code init} method.
     */
    @Override
    public void init(Object agent) {
        super.init(agent);
        this.systemPromptBuilder = getAttribute(agent, "system_prompt_builder");
        registerExperienceTool(agent);
        LOG.info("[ExperienceRail] Initialized with experience_dir={}", experienceDir);
    }

    /**
     * Uninitialize with agent - remove registered tools and prompt sections.
     * <p>
     * Mirrors Python's {@code uninit} method which:
     * <ul>
     *   <li>Removes tools from agent.ability_manager</li>
     *   <li>Removes tools from Runner.resource_mgr</li>
     *   <li>Removes prompt sections</li>
     * </ul>
     */
    @Override
    public void uninit(Object agent) {
        // Remove tools from agent.ability_manager
        if (agent instanceof DeepAgent deepAgent) {
            for (String toolName : new HashSet<>(ownedToolNames)) {
                deepAgent.getDelegate().getAbilityManager().remove(toolName);
                LOG.debug("[ExperienceRail] Removed tool '{}' from ability_manager", toolName);
            }
        } else if (agent != null) {
            Object abilityManager = getAttribute(agent, "ability_manager");
            if (abilityManager != null) {
                for (String toolName : new HashSet<>(ownedToolNames)) {
                    try {
                        abilityManager.getClass().getMethod("remove", String.class).invoke(abilityManager, toolName);
                        LOG.debug("[ExperienceRail] Removed tool '{}' from ability_manager", toolName);
                    } catch (Exception exc) {
                        LOG.warn("[ExperienceRail] Failed to remove tool '{}' from ability_manager: {}", toolName, exc.getMessage());
                    }
                }
            }
        }

        // Remove tools from Runner.resource_mgr
        for (String toolId : new HashSet<>(ownedToolIds)) {
            try {
                if (Runner.resourceMgr().getTool(toolId) != null) {
                    Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
                    LOG.debug("[ExperienceRail] Removed tool '{}' from resource_mgr", toolId);
                }
            } catch (Exception exc) {
                LOG.warn("[ExperienceRail] Failed to remove tool '{}' from resource_mgr: {}", toolId, exc.getMessage());
            }
        }

        ownedToolIds.clear();
        ownedToolNames.clear();

        // Remove prompt sections
        if (systemPromptBuilder != null) {
            try {
                // Python: self.system_prompt_builder.remove_section(SectionName.MEMORY)
                systemPromptBuilder.getClass().getMethod("removeSection", String.class)
                        .invoke(systemPromptBuilder, "MEMORY");
            } catch (Exception e) {
                LOG.debug("[ExperienceRail] Failed to remove prompt section");
            }
            systemPromptBuilder = null;
        }

        LOG.info("[ExperienceRail] Uninitialized");
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        try {
            systemPromptBuilder.getClass().getMethod("removeSection", String.class)
                    .invoke(systemPromptBuilder, "MEMORY");
            systemPromptBuilder.getClass().getMethod("addSection", Object.class)
                    .invoke(systemPromptBuilder, buildExperienceSection(language, experienceDir));
        } catch (ReflectiveOperationException ignored) {
            LOG.debug("[ExperienceRail] Failed to refresh experience prompt section");
        }
    }

    /**
     * Register experience_search tool to agent.
     * <p>
     * Mirrors Python's {@code _register_experience_tool} method.
     */
    private void registerExperienceTool(Object agent) {
        if (agent == null) {
            return;
        }

        Object abilityManager = getAttribute(agent, "ability_manager");
        boolean deepAgentPath = agent instanceof DeepAgent;
        if (abilityManager == null && !deepAgentPath) {
            LOG.warn("[ExperienceRail] Agent has no ability_manager, cannot register tool");
            return;
        }

        try {
            // Create experience search tool
            ExperienceSearchTool tool = new ExperienceSearchTool(experienceDir, language);

            // Add to Runner.resource_mgr if not existing
            String toolId = tool.getCard().getId();
            if (Runner.resourceMgr().getTool(toolId) == null) {
                Runner.resourceMgr().addTool(tool, null);
                ownedToolIds.add(toolId);
                LOG.debug("[ExperienceRail] Added tool '{}' to resource_mgr", toolId);
            }

            if (agent instanceof DeepAgent deepAgent) {
                deepAgent.getDelegate().getAbilityManager().add(tool.getCard());
                ownedToolNames.add(tool.getCard().getName());
                LOG.info("[ExperienceRail] Registered tool '{}'", tool.getCard().getName());
                return;
            }

            // Add to agent.ability_manager
            Object card = tool.getCard();
            Object result = abilityManager.getClass().getMethod("add", Object.class).invoke(abilityManager, card);
            // Check if tool was added successfully
            try {
                Boolean added = (Boolean) result.getClass().getMethod("getAdded").invoke(result);
                if (added != null && added) {
                    ownedToolNames.add(tool.getCard().getName());
                    LOG.info("[ExperienceRail] Registered tool '{}'", tool.getCard().getName());
                }
            } catch (Exception e) {
                // Assume added if no exception
                ownedToolNames.add(tool.getCard().getName());
                LOG.info("[ExperienceRail] Registered tool '{}'", tool.getCard().getName());
            }

        } catch (Exception e) {
            LOG.warn("[ExperienceRail] Failed to register experience tool: {}", e.getMessage());
        }
    }

    private Object getAttribute(Object obj, String name) {
        if (obj == null) return null;
        try {
            // Try getter method first
            try {
                return obj.getClass().getMethod("get" + capitalize(name)).invoke(obj);
            } catch (NoSuchMethodException e) {
                // Try field access
                try {
                    java.lang.reflect.Field field = obj.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (Exception ex) {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static String buildExperienceSection(String language, String experienceDir) {
        if ("en".equals(language)) {
            return "## Experience Library\n\nThe experience library lives at `" + experienceDir
                    + "`.\nUse `experience_search` when reviewing prior optimizations, failures, and insights.";
        }
        return "## Experience Library\n\n经验库位于 `" + experienceDir
                + "`。\n需要回顾历史优化、失败案例和洞察时，使用 `experience_search`。";
    }

    /**
     * Get the experience directory.
     */
    public String getExperienceDir() {
        return experienceDir;
    }

    /**
     * Get the language.
     */
    public String getLanguage() {
        return language;
    }
}
