/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.ProgressiveToolRailSection;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks tool-call progression hints.
 *
 * <p>Mirrors Python's {@code ProgressiveToolRail} in
 * {@code openjiuwen/harness/rails/progressive_tool_rail.py}.</p>
 */
public class ProgressiveToolRail extends DeepAgentRail {

    static final String VISIBLE_TOOLS_KEY = "__progressive_visible_tool_names__";

    private final Map<String, Integer> toolUseCounts = new LinkedHashMap<>();
    private final DeepAgentConfig config;
    private final Set<String> defaultVisibleTools = new LinkedHashSet<>();
    private final Set<String> alwaysVisibleTools = new LinkedHashSet<>();
    private final Set<String> metaToolNames = new LinkedHashSet<>();
    private final List<ToolInfo> cachedAllToolInfos = new ArrayList<>();
    private int maxLoadedTools = 12;

    public ProgressiveToolRail() {
        this(new DeepAgentConfig());
    }

    public ProgressiveToolRail(DeepAgentConfig config) {
        setPriority(60);
        this.config = config == null ? new DeepAgentConfig() : config;
        this.defaultVisibleTools.addAll(this.config.getProgressiveToolDefaultVisibleTools());
        this.alwaysVisibleTools.addAll(this.config.getProgressiveToolAlwaysVisibleTools());
        this.maxLoadedTools = this.config.getProgressiveToolMaxLoadedTools();
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        addPromptSections(ctx);
        filterCallableTools(ctx);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        String name = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (!name.isBlank()) {
            toolUseCounts.merge(name, 1, Integer::sum);
        }
        ctx.put("tool_use_counts", new LinkedHashMap<>(toolUseCounts));
    }

    public Map<String, Integer> getToolUseCounts() {
        return new LinkedHashMap<>(toolUseCounts);
    }

    void seedCachedTools(Set<String> metaToolNames, List<ToolInfo> allToolInfos) {
        this.metaToolNames.clear();
        if (metaToolNames != null) {
            this.metaToolNames.addAll(metaToolNames);
        }
        this.cachedAllToolInfos.clear();
        if (allToolInfos != null) {
            this.cachedAllToolInfos.addAll(allToolInfos);
        }
    }

    private void addPromptSections(CallbackContext ctx) {
        SystemPromptBuilder builder = promptBuilder(ctx);
        if (builder == null) {
            return;
        }
        builder.addSection(ProgressiveToolRailSection.buildMultilingualNavigationSection(
                navigationEntries("cn"),
                navigationEntries("en")));
        builder.addSection(ProgressiveToolRailSection.buildMultilingualProgressiveToolRulesSection());
    }

    private List<String> navigationEntries(String language) {
        Set<String> baseline = new LinkedHashSet<>(alwaysVisibleTools);
        baseline.addAll(defaultVisibleTools);
        List<String> entries = new ArrayList<>();
        for (ToolInfo info : cachedAllToolInfos) {
            String name = info == null ? "" : value(info.getName());
            if (name.isBlank() || metaToolNames.contains(name) || !baseline.contains(name)) {
                continue;
            }
            String status = "en".equals(language) ? "callable" : "可调用";
            entries.add(ProgressiveToolRailSection.buildNavigationEntry(
                    name,
                    "general",
                    status,
                    value(info.getDescription()),
                    language));
        }
        return entries;
    }

    private void filterCallableTools(CallbackContext ctx) {
        Object rawTools = ctx.get("tools");
        if (!(rawTools instanceof List<?> list)) {
            return;
        }

        Set<String> visible = new LinkedHashSet<>();
        visible.addAll(metaToolNames);
        visible.addAll(alwaysVisibleTools);
        visible.addAll(visibleTools(ctx.get("session")));

        List<Object> filtered = new ArrayList<>();
        for (Object tool : list) {
            String name = toolName(tool);
            if (!name.isBlank() && visible.contains(name)) {
                filtered.add(tool);
            }
        }
        ctx.put("tools", filtered);
    }

    private List<String> visibleTools(Object session) {
        Object state = sessionState(session, VISIBLE_TOOLS_KEY);
        if (!(state instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String value = value(item);
            if (!value.isBlank() && result.size() < maxLoadedTools) {
                result.add(value);
            }
        }
        return result;
    }

    private static Object sessionState(Object session, String key) {
        if (session instanceof AgentSessionApi api) {
            return api.getState(key);
        }
        return invokeNoArg(session, "getState", "get_state", key);
    }

    private static SystemPromptBuilder promptBuilder(CallbackContext ctx) {
        Object direct = ctx.get("system_prompt_builder");
        if (direct instanceof SystemPromptBuilder builder) {
            return builder;
        }
        return promptBuilderFrom(ctx.getAgent());
    }

    private static SystemPromptBuilder promptBuilderFrom(Object candidate) {
        if (candidate == null) {
            return null;
        }
        Object value = invokeNoArg(candidate, "getSystemPromptBuilder", "getPromptBuilder", null);
        if (value instanceof SystemPromptBuilder builder) {
            return builder;
        }
        Object fieldValue = readField(candidate, "systemPromptBuilder", "system_prompt_builder", "promptBuilder");
        return fieldValue instanceof SystemPromptBuilder builder ? builder : null;
    }

    private static Object invokeNoArg(Object target, String firstName, String secondName, String argument) {
        if (target == null) {
            return null;
        }
        for (String name : List.of(firstName, secondName)) {
            try {
                Method method = target.getClass().getMethod(name, argument == null ? new Class<?>[]{} : new Class<?>[]{String.class});
                return argument == null ? method.invoke(target) : method.invoke(target, argument);
            } catch (ReflectiveOperationException ignored) {
                // Try the next Java/Python-style accessor spelling.
            }
        }
        return null;
    }

    private static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next field spelling.
            }
        }
        return null;
    }

    private static String toolName(Object tool) {
        if (tool instanceof ToolInfo info) {
            return value(info.getName());
        }
        if (tool instanceof Tool value && value.getCard() != null) {
            return value(value.getCard().getName());
        }
        if (tool instanceof Map<?, ?> map && map.containsKey("name")) {
            return value(map.get("name"));
        }
        return value(tool);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
