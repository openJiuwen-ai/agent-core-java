/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic registries for serializable DeepAgent rail and tool specs.
 */
public final class DeepAgentSpecRegistry {

    private static final Map<String, RailFactory<?>> RAIL_TYPE_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, ToolFactory<?>> TOOL_TYPE_REGISTRY = new LinkedHashMap<>();

    private DeepAgentSpecRegistry() {
    }

    public static void registerRailType(String name, RailFactory<?> factory) {
        RAIL_TYPE_REGISTRY.put(name, factory);
    }

    public static void registerToolType(String name, ToolFactory<?> factory) {
        TOOL_TYPE_REGISTRY.put(name, factory);
    }

    public static Object buildRail(String type, Map<String, Object> params, String language, Workspace workspace) {
        ensureBuiltinRailsRegistered();
        RailFactory<?> factory = RAIL_TYPE_REGISTRY.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown rail type '" + type + "'. Registered types: " + RAIL_TYPE_REGISTRY.keySet());
        }
        Map<String, Object> values = new LinkedHashMap<>(params == null ? Map.of() : params);
        values.putIfAbsent("language", language);
        if ("skill_use".equals(type) && !values.containsKey("skills_dir")) {
            List<String> dirs = new ArrayList<>();
            if (workspace != null && workspace.getNodePath("skills") != null) {
                dirs.add(workspace.getNodePath("skills").toString());
            }
            dirs.add("~/.openjiuwen/workspace/skills");
            dirs.add("~/.claude/skills");
            values.put("skills_dir", dirs);
        }
        return factory.build(values);
    }

    public static Object buildTool(String type, Map<String, Object> params, String language, String toolId) {
        ensureBuiltinToolsRegistered();
        ToolFactory<?> factory = TOOL_TYPE_REGISTRY.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown tool type '" + type + "'. Registered types: " + TOOL_TYPE_REGISTRY.keySet());
        }
        Map<String, Object> values = new LinkedHashMap<>(params == null ? Map.of() : params);
        values.putIfAbsent("language", language);
        if (toolId != null && !toolId.isBlank()) {
            values.putIfAbsent("tool_id", toolId);
        }
        return factory.build(values);
    }

    public static Map<String, RailFactory<?>> railRegistryView() {
        ensureBuiltinRailsRegistered();
        return Map.copyOf(RAIL_TYPE_REGISTRY);
    }

    public static Map<String, ToolFactory<?>> toolRegistryView() {
        ensureBuiltinToolsRegistered();
        return Map.copyOf(TOOL_TYPE_REGISTRY);
    }

    static void ensureBuiltinRailsRegistered() {
        if (!RAIL_TYPE_REGISTRY.isEmpty()) {
            return;
        }
        RAIL_TYPE_REGISTRY.put("task_planning", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("skill_use", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("subagent", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("filesystem", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("context_engineering", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("token_tracking", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("tool_tracking", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("ask_user", DeepAgentSpecRegistry::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("confirm_interrupt", DeepAgentSpecRegistry::dynamicConfigMap);
    }

    static void ensureBuiltinToolsRegistered() {
        if (!TOOL_TYPE_REGISTRY.isEmpty()) {
            return;
        }
        TOOL_TYPE_REGISTRY.put("web_search", DeepAgentSpecRegistry::dynamicConfigMap);
        TOOL_TYPE_REGISTRY.put("web_fetch", DeepAgentSpecRegistry::dynamicConfigMap);
    }

    private static Map<String, Object> dynamicConfigMap(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }

    /**
     * Dynamic rail factory for registry-backed rail specs.
     *
     * <p>Mirrors Python rail registry entries in
     * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
     */
    @FunctionalInterface
    public interface RailFactory<T> {
        T build(Map<String, Object> values);
    }

    /**
     * Dynamic tool factory for registry-backed builtin tool specs.
     *
     * <p>Mirrors Python tool registry entries in
     * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
     */
    @FunctionalInterface
    public interface ToolFactory<T> {
        T build(Map<String, Object> values);
    }
}
