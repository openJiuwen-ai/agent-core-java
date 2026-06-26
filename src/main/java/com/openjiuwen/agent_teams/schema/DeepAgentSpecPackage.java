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
 * Module facade and dynamic registries for serializable DeepAgent specs.
 *
 * <p>Mirrors Python's registry helpers in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public final class DeepAgentSpecPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/schema/deep_agent_spec.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AudioModelSpec",
            "BuiltinToolSpec",
            "DeepAgentSpec",
            "ProgressiveToolSpec",
            "RailSpec",
            "SubAgentSpec",
            "SysOperationSpec",
            "TeamModelConfig",
            "VisionModelSpec",
            "WorkspaceSpec",
            "register_rail_type",
            "register_tool_type"
    );

    private static final Map<String, RailFactory<?>> RAIL_TYPE_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, ToolFactory<?>> TOOL_TYPE_REGISTRY = new LinkedHashMap<>();

    private DeepAgentSpecPackage() {
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
        RAIL_TYPE_REGISTRY.put("task_planning", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("skill_use", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("subagent", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("filesystem", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("context_engineering", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("token_tracking", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("tool_tracking", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("ask_user", DeepAgentSpecPackage::dynamicConfigMap);
        RAIL_TYPE_REGISTRY.put("confirm_interrupt", DeepAgentSpecPackage::dynamicConfigMap);
    }

    static void ensureBuiltinToolsRegistered() {
        if (!TOOL_TYPE_REGISTRY.isEmpty()) {
            return;
        }
        TOOL_TYPE_REGISTRY.put("web_search", DeepAgentSpecPackage::dynamicConfigMap);
        TOOL_TYPE_REGISTRY.put("web_fetch", DeepAgentSpecPackage::dynamicConfigMap);
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
