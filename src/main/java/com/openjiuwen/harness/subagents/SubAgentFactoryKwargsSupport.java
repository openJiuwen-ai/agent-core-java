/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SubAgentFactoryKwargsSupport
 *
 * @since 0.1.7
 */
final class SubAgentFactoryKwargsSupport {
    /**
     * SubAgentFactoryKwargsSupport.
     * 
     * @since 0.1.7
     */
    private SubAgentFactoryKwargsSupport() {
    }

    static Map<String, Object> copy(Map<String, Object> factoryKwargs) {
        return factoryKwargs != null ? new LinkedHashMap<>(factoryKwargs) : new LinkedHashMap<>();
    }

    static AgentCard resolveAgentCard(Map<String, Object> kwargs, String defaultName, String defaultDescription) {
        Object card = first(kwargs, List.of("card", "agent_card"));
        if (card instanceof AgentCard agentCard) {
            return agentCard;
        }
        Map<String, Object> cardMap = card instanceof Map<?, ?> map ? normalizeMap(map) : Map.of();
        String name = stringValue(first(kwargs, List.of("name", "agent_name", "card_name")));
        if (name == null) {
            name = stringValue(cardMap.get("name"));
        }
        String description = stringValue(first(kwargs, List.of("description", "agent_description")));
        if (description == null) {
            description = stringValue(cardMap.get("description"));
        }
        String id = stringValue(first(kwargs, List.of("id", "agent_id")));
        if (id == null) {
            id = stringValue(cardMap.get("id"));
        }
        return AgentCard.builder().id(id).name(name != null ? name : defaultName)
                .description(description != null ? description : defaultDescription)
                .inputParams(firstNonNull(new Object[]{first(kwargs, List.of("input_params", "inputParams")),
                        cardMap.get("input_params"), cardMap.get("inputParams")}))
                .outputParams(firstNonNull(new Object[]{first(kwargs, List.of("output_params", "outputParams")),
                        cardMap.get("output_params"), cardMap.get("outputParams")}))
                .build();
    }

    static String systemPrompt(Map<String, Object> kwargs, String defaultPrompt) {
        String configured = stringValue(first(kwargs, List.of("system_prompt", "systemPrompt", "prompt")));
        return configured != null ? configured : defaultPrompt;
    }

    static int maxIterations(Map<String, Object> kwargs, int defaultValue) {
        Integer configured = integer(first(kwargs, List.of("max_iterations", "maxIterations")));
        return configured != null ? configured.intValue() : defaultValue;
    }

    static void applyCommonOverrides(SubAgentConfig config, Map<String, Object> kwargs) {
        if (config == null || kwargs == null || kwargs.isEmpty()) {
            return;
        }
        optionalString(first(kwargs, List.of("execution_mode", "executionMode"))).ifPresent(config::setExecutionMode);
        optionalString(first(kwargs, List.of("role"))).ifPresent(config::setRole);
        optionalString(first(kwargs, List.of("workspace", "workspace_path", "workspacePath")))
                .ifPresent(config::setWorkspacePath);
        optionalString(first(kwargs, List.of("prompt_mode", "promptMode"))).ifPresent(config::setPromptMode);
        optionalString(first(kwargs, List.of("skill_mode", "skillMode"))).ifPresent(config::setSkillMode);

        Boolean isTaskLoopEnabled = toBooleanValue(first(kwargs, List.of("enable_task_loop", "isTaskLoopEnabled")));
        if (isTaskLoopEnabled != null) {
            config.setEnableTaskLoop(isTaskLoopEnabled);
        }
        Boolean isWorkDirRestricted =
            toBooleanValue(first(kwargs, List.of("restrict_to_work_dir", "restrictToWorkDir", "isRestrictToWorkDir")));
        if (isWorkDirRestricted != null) {
            config.setRestrictToWorkDir(isWorkDirRestricted);
        }

        Object model = first(kwargs, List.of("model"));
        if (model != null) {
            config.setModel(model);
        }
        Object backend = first(kwargs, List.of("backend"));
        if (backend != null) {
            config.setBackend(backend);
        }
        Object sysOperation = first(kwargs, List.of("sys_operation", "sysOperation"));
        if (sysOperation instanceof SysOperation op) {
            config.setSysOperation(op);
        }

        List<Object> tools = objectList(first(kwargs, List.of("tools")));
        if (tools != null) {
            config.setTools(tools);
        }
        List<Object> subagents = objectList(first(kwargs, List.of("subagents")));
        if (subagents != null) {
            config.setSubagents(subagents);
        }
        List<McpServerConfig> mcps = mcpList(first(kwargs, List.of("mcps")));
        if (mcps != null) {
            config.setMcps(mcps);
        }
        List<String> skills = stringList(first(kwargs, List.of("skills")));
        if (skills != null) {
            config.setSkills(skills);
        }
        List<String> skillDirectories =
            stringList(first(kwargs, List.of("skill_directories", "skillDirectories", "skills_dir", "skill_dirs")));
        if (skillDirectories != null) {
            config.setSkillDirectories(skillDirectories);
        }
        Map<String, Object> metadata = mapValue(first(kwargs, List.of("metadata")));
        if (metadata != null) {
            Map<String, Object> merged =
                new LinkedHashMap<>(config.getMetadata() != null ? config.getMetadata() : Map.of());
            merged.putAll(metadata);
            config.setMetadata(merged);
        }
    }

    /**
     * first.
     * 
     * @param values values
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object first(Map<String, Object> values, List<String> keys) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    /**
     * firstNonNull.
     * 
     * @param values values
     * @return the result
     * @since 0.1.7
     */
    private static Object firstNonNull(Object[] values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * optionalString.
     * 
     * @param value value
     * @return Optional<String>
     * @since 0.1.7
     */
    private static java.util.Optional<String> optionalString(Object value) {
        return java.util.Optional.ofNullable(value instanceof String text && !text.isBlank() ? text : null);
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * integer.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * toBooleanValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Boolean toBooleanValue(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    /**
     * objectList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> objectList(Object value) {
        if (value == null) {
            return java.util.Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object.class::cast).collect(ArrayList::new, ArrayList::add,
                    ArrayList::addAll);
        }
        return new ArrayList<>(List.of(value));
    }

    /**
     * stringList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(Object value) {
        if (value == null) {
            return java.util.Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(SubAgentFactoryKwargsSupport::stringValue).filter(item -> item != null)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
        String single = stringValue(value);
        return single != null ? new ArrayList<>(List.of(single)) : new ArrayList<>();
    }

    /**
     * mcpList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<McpServerConfig> mcpList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return java.util.Collections.emptyList();
        }
        List<McpServerConfig> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof McpServerConfig config) {
                result.add(config);
            }
        }
        return result;
    }

    /**
     * mapValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        return null;
    }

    /**
     * normalizeMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> normalizeMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
