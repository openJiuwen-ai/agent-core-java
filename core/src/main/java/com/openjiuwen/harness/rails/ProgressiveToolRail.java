/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.ProgressiveToolRailSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
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
    private SystemPromptBuilder systemPromptBuilder;

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

    /**
     * Resolve the inner ReAct agent's prompt builder and add the sections;
     * {@link #beforeInvoke} adds them again if the builder has been replaced since.
     *
     * @param agent deep agent
     */
    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        systemPromptBuilder = promptBuilderFrom(agent);
        addPromptSections(systemPromptBuilder);
    }

    /**
     * Removes the prompt sections registered by this rail.
     *
     * @param agent deep agent
     */
    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.TOOL_NAVIGATION);
            systemPromptBuilder.removeSection(SectionName.PROGRESSIVE_TOOL_RULES);
        }
        systemPromptBuilder = null;
    }

    /**
     * The first model request renders the system prompt before {@code beforeModelCall} runs,
     * so the sections must already be in place when the invocation starts (e.g. after
     * {@code ReActAgent.configure()} replaced the prompt builder).
     *
     * @param ctx callback context
     */
    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (ctx != null) {
            addPromptSections(promptBuilder(ctx));
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        addPromptSections(promptBuilder(ctx));
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

    /**
     * Persistent, because ReActAgent clears transient sections at the start of every inner
     * invocation; {@link #uninit} removes them.
     *
     * @param builder prompt builder to receive the sections, may be null
     */
    private void addPromptSections(SystemPromptBuilder builder) {
        if (builder == null) {
            return;
        }
        builder.addPersistentSection(ProgressiveToolRailSection.buildMultilingualNavigationSection(
                navigationEntries("cn"),
                navigationEntries("en")));
        builder.addPersistentSection(ProgressiveToolRailSection.buildMultilingualProgressiveToolRulesSection());
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

    private SystemPromptBuilder promptBuilder(CallbackContext ctx) {
        Object direct = ctx.get("system_prompt_builder");
        if (direct instanceof SystemPromptBuilder builder) {
            return builder;
        }
        // Resolve on every call: ReActAgent.configure() replaces its prompt builder.
        SystemPromptBuilder current = promptBuilderFrom(ctx.getAgent());
        if (current != null) {
            systemPromptBuilder = current;
        }
        return systemPromptBuilder;
    }

    /**
     * The system prompt is rendered by the inner ReAct agent, so look there as well as on the deep agent.
     *
     * @param candidate object to resolve the prompt builder from, may be null
     * @return the resolved prompt builder, or null when none can be found
     */
    private static SystemPromptBuilder promptBuilderFrom(Object candidate) {
        if (candidate == null) {
            return null;
        }
        Object value = invokeNoArg(candidate, "getSystemPromptBuilder", "getPromptBuilder", null);
        if (value instanceof SystemPromptBuilder builder) {
            return builder;
        }
        Object fieldValue = readField(candidate, "systemPromptBuilder", "system_prompt_builder", "promptBuilder");
        if (fieldValue instanceof SystemPromptBuilder builder) {
            return builder;
        }
        if (candidate instanceof DeepAgent deepAgent && deepAgent.reactAgent() != candidate) {
            return promptBuilderFrom(deepAgent.reactAgent());
        }
        return null;
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
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    // Try the superclass, then the next field spelling.
                }
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
