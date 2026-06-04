/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.prompts.sections.ProgressiveToolRailSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rail that enables progressive tool discovery and callable-tool filtering.
 * <p>
 * Registers search_tools and load_tools meta-tools, manages visible tool
 * sets, and filters tool lists based on what the agent has currently loaded.
 * <p>
 * Mirrors Python's {@code ProgressiveToolRail} in
 * {@code openjiuwen.harness.rails.progressive_tool_rail}.
 */
public class ProgressiveToolRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressiveToolRail.class);
    private static final String VISIBLE_TOOLS_KEY = "__progressive_visible_tool_names__";
    private static final Set<String> META_TOOL_NAMES = Set.of("search_tools", "load_tools");

    /** Rail priority. */
    public static final int PRIORITY = 90;

    private final java.util.Set<String> defaultVisibleTools = new java.util.HashSet<>();
    private final java.util.Set<String> alwaysVisibleTools = new java.util.HashSet<>();
    private final java.util.Set<String> loadedToolNames = new java.util.LinkedHashSet<>();

    public ProgressiveToolRail(java.util.Set<String> defaultVisibleTools,
                               java.util.Set<String> alwaysVisibleTools,
                               Integer maxLoadedTools) {
        super();
        setPriority(PRIORITY);
        if (defaultVisibleTools != null) {
            this.defaultVisibleTools.addAll(defaultVisibleTools);
        }
        if (alwaysVisibleTools != null) {
            this.alwaysVisibleTools.addAll(alwaysVisibleTools);
        }
    }

    /** Get the set of currently loaded tool names. */
    public java.util.Set<String> getLoadedToolNames() {
        return java.util.Collections.unmodifiableSet(loadedToolNames);
    }

    /** Load a tool by name. */
    public void loadTool(String toolName) {
        loadedToolNames.add(toolName);
        LOG.debug("[ProgressiveToolRail] Loaded tool: {}", toolName);
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (ctx == null || !(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }

        SystemPromptBuilder builder = resolveSystemPromptBuilder(ctx.getAgent());
        if (builder != null) {
            builder.addSection(ProgressiveToolRailSection.build());
            builder.addSection(new PromptSection(
                    "progressive_tool_navigation",
                    Map.of(
                            "cn",
                            "## 工具导航\n\n- `search_tools` 用于搜索工具注册表\n- `load_tools` 用于把目标工具变成当前 session 可调用工具",
                            "en",
                            "## Tool Navigation\n\n- `search_tools` searches the tool registry\n- `load_tools` makes chosen tools callable in the current session"
                    ),
                    57
            ));
        }

        List<ToolInfo> tools = inputs.getTools();
        if (tools == null) {
            return;
        }

        Set<String> visibleNames = new LinkedHashSet<>();
        visibleNames.addAll(META_TOOL_NAMES);
        visibleNames.addAll(alwaysVisibleTools);
        visibleNames.addAll(defaultVisibleTools);
        visibleNames.addAll(loadedToolNames);
        visibleNames.addAll(readSessionVisibleTools(ctx.getSession()));

        List<ToolInfo> filtered = new ArrayList<>();
        for (ToolInfo tool : tools) {
            if (tool != null && tool.getName() != null && visibleNames.contains(tool.getName())) {
                filtered.add(tool);
            }
        }
        inputs.setTools(filtered);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[ProgressiveToolRail] Initialized with {} default visible tools",
                defaultVisibleTools.size());
    }

    @Override
    public void uninit(Object agent) {
        loadedToolNames.clear();
        LOG.info("[ProgressiveToolRail] Uninitialized");
    }

    private static Set<String> readSessionVisibleTools(Session session) {
        if (session == null) {
            return Set.of();
        }
        Object value = session.getState(VISIBLE_TOOLS_KEY);
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object agent) {
        Object builder = invokeNoArg(agent, "getSystemPromptBuilder");
        if (builder instanceof SystemPromptBuilder typed) {
            return typed;
        }
        builder = readProperty(agent, "systemPromptBuilder", "system_prompt_builder", "builder");
        return builder instanceof SystemPromptBuilder typed ? typed : null;
    }

    private static Object readProperty(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Object value = invokeNoArg(target, getterName(name));
            if (value != null) {
                return value;
            }
            value = invokeNoArg(target, name);
            if (value != null) {
                return value;
            }
            value = readField(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String getterName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }
}
