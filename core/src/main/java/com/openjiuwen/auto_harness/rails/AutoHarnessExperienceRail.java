/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.tools.ExperienceSearchTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.AddAbilityResult;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.core.runner.Runner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Registers the experience search tool and injects the experience prompt section.
 *
 * <p>Mirrors Python's {@code AutoHarnessExperienceRail} and module helper in
 * {@code openjiuwen/auto_harness/rails/experience_rail.py}.</p>
 */
public class AutoHarnessExperienceRail extends DeepAgentRail {

    public static final int PRIORITY = 80;
    public static final int EXPERIENCE_SECTION_PRIORITY = 85;
    public static final String DEFAULT_EXPERIENCE_DIR = ".auto_harness/experience";
    public static final String DEFAULT_LANGUAGE = "cn";

    private static final Logger LOGGER = Logger.getLogger(AutoHarnessExperienceRail.class.getName());

    private final String experienceDir;
    private final String language;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private SystemPromptBuilder systemPromptBuilder;
    private AbilityManager abilityManager;

    public AutoHarnessExperienceRail(String experienceDir) {
        this(experienceDir, DEFAULT_LANGUAGE);
    }

    public AutoHarnessExperienceRail(String experienceDir, String language) {
        this.experienceDir = experienceDir == null ? DEFAULT_EXPERIENCE_DIR : experienceDir;
        this.language = language == null ? DEFAULT_LANGUAGE : language;
        setPriority(PRIORITY);
    }

    public static PromptSection buildExperienceSection() {
        return buildExperienceSection(DEFAULT_LANGUAGE, DEFAULT_EXPERIENCE_DIR);
    }

    public static PromptSection buildExperienceSection(String language, String experienceDir) {
        String resolvedDir = experienceDir == null ? DEFAULT_EXPERIENCE_DIR : experienceDir;
        Map<String, String> content = new LinkedHashMap<>();
        content.put("cn", "## Experience Library\n\n"
                + "经验库位于 `" + resolvedDir + "`。\n"
                + "需要回顾历史优化、失败案例和洞察时，使用 `experience_search`。");
        content.put("en", "## Experience Library\n\n"
                + "The experience library lives at `" + resolvedDir + "`.\n"
                + "Use `experience_search` when reviewing prior optimizations, failures, and insights.");
        return new PromptSection(SectionName.MEMORY, content, EXPERIENCE_SECTION_PRIORITY);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        Object reactAgent = agent == null ? null : agent.reactAgent();
        systemPromptBuilder = resolveSystemPromptBuilder(agent, reactAgent);
        abilityManager = resolveAbilityManager(agent, reactAgent);
        registerExperienceTool(agent);
    }

    @Override
    public void uninit(DeepAgent agent) {
        removeOwnedAbilities(agent);
        removeOwnedResources();
        ownedToolIds.clear();
        ownedToolNames.clear();
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.MEMORY);
            systemPromptBuilder = null;
        }
        abilityManager = null;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        systemPromptBuilder.removeSection(SectionName.MEMORY);
        systemPromptBuilder.addSection(buildExperienceSection(systemPromptBuilder.getLanguage(), experienceDir));
    }

    public String getExperienceDir() {
        return experienceDir;
    }

    public String getLanguage() {
        return language;
    }

    Set<String> getOwnedToolNames() {
        return new LinkedHashSet<>(ownedToolNames);
    }

    private void registerExperienceTool(DeepAgent agent) {
        if (abilityManager == null && agent == null) {
            return;
        }
        ExperienceSearchTool tool = new ExperienceSearchTool(experienceDir, null, language);
        String toolId = tool.getCard().getId();
        if (Runner.resourceMgr().getTool(toolId) == null) {
            Runner.resourceMgr().addTool(tool);
            ownedToolIds.add(toolId);
        }

        if (abilityManager != null) {
            AddAbilityResult result = abilityManager.add(tool.getCard());
            if (result.isAdded()) {
                ownedToolNames.add(tool.getCard().getName());
            }
            return;
        }

        agent.registerTool(tool);
        ownedToolNames.add(tool.getCard().getName());
    }

    private void removeOwnedAbilities(DeepAgent agent) {
        for (String toolName : Set.copyOf(ownedToolNames)) {
            if (abilityManager != null) {
                abilityManager.remove(toolName);
            } else if (agent != null) {
                agent.unregisterTool(toolName);
            }
        }
    }

    private void removeOwnedResources() {
        for (String toolId : Set.copyOf(ownedToolIds)) {
            Tool existing = Runner.resourceMgr().getTool(toolId);
            if (existing == null) {
                continue;
            }
            try {
                Object result = Runner.resourceMgr().removeTool(toolId);
                if (result instanceof com.openjiuwen.core.runner.resourcemanager.Result<?, ?> typedResult
                        && typedResult.isErr()) {
                    LOGGER.warning("Failed to remove experience tool: " + toolId);
                }
            } catch (RuntimeException error) {
                LOGGER.warning("Failed to remove experience tool: " + toolId);
            }
        }
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object... candidates) {
        for (Object candidate : candidates) {
            Object value = invokeNoArg(candidate, "getSystemPromptBuilder", "getPromptBuilder");
            if (value instanceof SystemPromptBuilder builder) {
                return builder;
            }
            Object fieldValue = readField(candidate, "systemPromptBuilder", "system_prompt_builder", "promptBuilder");
            if (fieldValue instanceof SystemPromptBuilder builder) {
                return builder;
            }
        }
        return null;
    }

    private static AbilityManager resolveAbilityManager(Object... candidates) {
        for (Object candidate : candidates) {
            Object value = invokeNoArg(candidate, "getAbilityManager", "get_ability_manager");
            if (value instanceof AbilityManager manager) {
                return manager;
            }
            Object fieldValue = readField(candidate, "abilityManager", "ability_manager");
            if (fieldValue instanceof AbilityManager manager) {
                return manager;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next Python/Java accessor spelling.
            }
        }
        return null;
    }

    private static Object readField(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next field spelling.
                } catch (IllegalAccessException error) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
