/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.middlewares;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context Engineering Middleware for deep agents.
 * <p>
 * Mirrors Python's {@code context_engineering_middleware} module in {@code
 * openjiuwen.deepagents.middlewares}.
 * <p>
 * This middleware normalizes prompt sections and workspace-related context into a stable
 * Java-side structure that higher-level callers can inspect or forward into prompt builders.
 * 
 * @since 0.1.7
 */
public class ContextEngineeringMiddleware {
    /**
     * process.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> process(Object context) {
        if (context instanceof DeepAgent agent) {
            return fromConfig(agent.getConfig(), agent.getWorkspace().root().toString());
        }
        if (context instanceof DeepAgentConfig config) {
            return fromConfig(config, config.getWorkspacePath());
        }
        if (context instanceof Map<?, ?> map) {
            return fromMap(map);
        }
        if (context instanceof String text) {
            return Map.of("system_prompt", text, "workspace_path", "./", "language", "cn", "sections", List.of());
        }
        throw new IllegalArgumentException(
                "Unsupported context source: " + (context == null ? "null" : context.getClass().getName()));
    }

    /**
     * fromConfig.
     * 
     * @param config config
     * @param workspacePath workspacePath
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> fromConfig(DeepAgentConfig config, String workspacePath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("system_prompt", config.getSystemPrompt());
        result.put("workspace_path", workspacePath);
        result.put("language", config.getLanguage());
        result.put("completion_timeout", config.getCompletionTimeout());
        result.put("skill_directories", List.copyOf(config.getSkillDirectories()));
        result.put("skill_mode", config.getSkillMode());
        result.put("sections", normalizeSections(config.getExtraPromptSections()));
        return result;
    }

    /**
     * fromMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> fromMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("system_prompt", stringValue(source.get("system_prompt")));
        result.put("workspace_path",
                stringValue(source.containsKey("workspace_path") ? source.get("workspace_path") : "./"));
        result.put("language", stringValue(source.containsKey("language") ? source.get("language") : "cn"));
        result.put("completion_timeout", source.get("completion_timeout"));
        result.put("skill_directories", listOfStrings(source.get("skill_directories")));
        result.put("skill_mode", stringValue(source.containsKey("skill_mode") ? source.get("skill_mode") : "all"));
        Object sections = source.containsKey("sections") ? source.get("sections") : source.get("extra_prompt_sections");
        result.put("sections", normalizeSections(sections));
        return result;
    }

    /**
     * normalizeSections.
     * 
     * @param sections sections
     * @return the result
     * @since 0.1.7
     */
    private List<Map<String, Object>> normalizeSections(Object sections) {
        if (!(sections instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> section = new LinkedHashMap<>();
                section.put("name", stringValue(map.get("name")));
                Object priority = map.get("priority");
                section.put("priority", priority instanceof Number number ? number.intValue() : 30);
                Object content = map.get("content");
                section.put("content",
                        content instanceof Map<?, ?> contentMap
                                ? stringifyMap(contentMap)
                                : Map.of("cn", stringValue(content)));
                normalized.add(section);
            }
        }
        normalized.sort(Comparator.comparingInt(section -> {
            Object value = section.getOrDefault("priority", 30);
            return value instanceof Integer priority ? priority : 30;
        }));
        return List.copyOf(normalized);
    }

    /**
     * stringifyMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    private Map<String, String> stringifyMap(Map<?, ?> map) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * listOfStrings.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
