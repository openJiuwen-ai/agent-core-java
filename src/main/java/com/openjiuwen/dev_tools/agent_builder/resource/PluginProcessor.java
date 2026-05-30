/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugin resource preprocessing helpers.
 * <p>
 * Mirrors Python's {@code PluginProcessor} in
 * {@code openjiuwen.dev_tools.agent_builder.resource.processor}.
 */
public final class PluginProcessor {

    public static final Map<Integer, String> TYPE_MAP = Map.of(
            1, "string",
            2, "integer",
            3, "number",
            4, "boolean",
            5, "array",
            6, "object");

    private PluginProcessor() {
    }

    public record PreprocessResult(
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolPluginIdMap) {
    }

    public record RetrievedInfo(
            List<Map<String, Object>> toolList,
            Map<String, Map<String, Object>> retrievedPluginDict,
            Map<String, String> retrievedToolIdMap) {
    }

    public static String convertType(Object paramType) {
        if (paramType instanceof Number number) {
            return TYPE_MAP.getOrDefault(number.intValue(), "string");
        }
        if (paramType instanceof String value) {
            return value;
        }
        return "string";
    }

    public static PreprocessResult preprocess(List<Map<String, Object>> rawPlugins) {
        Map<String, Map<String, Object>> pluginDict = new LinkedHashMap<>();
        Map<String, String> toolPluginIdMap = new LinkedHashMap<>();

        for (Map<String, Object> plugin : safeList(rawPlugins)) {
            String pluginId = asString(plugin.get("plugin_id"));
            if (pluginId.isBlank()) {
                continue;
            }

            Map<String, Map<String, Object>> formattedTools = new LinkedHashMap<>();
            for (Map<String, Object> tool : mapsFrom(plugin.get("tools"))) {
                String toolId = asString(tool.get("tool_id"));
                if (toolId.isBlank()) {
                    continue;
                }

                toolPluginIdMap.put(toolId, pluginId);
                List<Map<String, Object>> inputParams = mapsFrom(tool.get("input_parameters"));
                List<Map<String, Object>> outputParams = mapsFrom(tool.get("output_parameters"));

                Map<String, Object> toolDict = new LinkedHashMap<>();
                toolDict.put("tool_id", toolId);
                toolDict.put("tool_name", asString(tool.get("tool_name")));
                toolDict.put("tool_desc", asString(tool.get("desc")));
                toolDict.put("code", asString(tool.get("code")));
                toolDict.put("language", asString(tool.get("language")));
                toolDict.put("input_parameters", inputParams);
                toolDict.put("output_parameters", outputParams);
                toolDict.put("ori_inputs", inputParams);
                toolDict.put("ori_outputs", outputParams);
                toolDict.put("inputs_for_dl_gen", formatParams(inputParams));
                toolDict.put("outputs_for_dl_gen", formatParams(outputParams));
                formattedTools.put(toolId, toolDict);
            }

            Map<String, Object> pluginDictItem = new LinkedHashMap<>();
            pluginDictItem.put("plugin_id", pluginId);
            pluginDictItem.put("plugin_name", asString(plugin.get("plugin_name")));
            pluginDictItem.put("plugin_desc", asString(plugin.get("plugin_desc")));
            pluginDictItem.put("plugin_version", valueOrDefault(plugin.get("plugin_version"), "draft"));
            pluginDictItem.put("tools", formattedTools);
            pluginDict.put(pluginId, pluginDictItem);
        }

        return new PreprocessResult(pluginDict, toolPluginIdMap);
    }

    public static List<Map<String, Object>> formatForPrompt(Map<String, Map<String, Object>> pluginDict) {
        if (pluginDict == null || pluginDict.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> plugin : pluginDict.values()) {
            List<Map<String, Object>> toolsBrief = new ArrayList<>();
            for (Map<String, Object> tool : mapValues(plugin.get("tools"))) {
                Map<String, Object> brief = new LinkedHashMap<>();
                brief.put("tool_id", tool.get("tool_id"));
                brief.put("tool_name", tool.get("tool_name"));
                brief.put("tool_desc", tool.get("tool_desc"));
                brief.put("code", valueOrDefault(tool.get("code"), ""));
                brief.put("language", valueOrDefault(tool.get("language"), ""));
                brief.put("input_parameters", convertParams(mapsFrom(tool.get("input_parameters"))));
                brief.put("output_parameters", convertParams(mapsFrom(tool.get("output_parameters"))));
                toolsBrief.add(brief);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("plugin_id", plugin.get("plugin_id"));
            item.put("plugin_name", plugin.get("plugin_name"));
            item.put("plugin_desc", plugin.get("plugin_desc"));
            item.put("tools", toolsBrief);
            result.add(item);
        }
        return result;
    }

    public static RetrievedInfo getRetrievedInfo(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolPluginIdMap) {
        return getRetrievedInfo(toolIdList, pluginDict, toolPluginIdMap, true);
    }

    @SuppressWarnings("unchecked")
    public static RetrievedInfo getRetrievedInfo(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolPluginIdMap,
            boolean needInputsOutputs) {
        List<Map<String, Object>> toolDetails = new ArrayList<>();
        Map<String, Map<String, Object>> retrievedPluginDict = new LinkedHashMap<>();
        Map<String, String> retrievedToolIdMap = new LinkedHashMap<>();

        for (String toolId : safeStrings(toolIdList)) {
            String pluginId = toolPluginIdMap != null ? toolPluginIdMap.get(toolId) : null;
            if (pluginId == null || pluginDict == null || !pluginDict.containsKey(pluginId)) {
                continue;
            }
            Map<String, Object> plugin = pluginDict.get(pluginId);
            Map<String, Map<String, Object>> tools = (Map<String, Map<String, Object>>) plugin.get("tools");
            if (tools == null || !tools.containsKey(toolId)) {
                continue;
            }

            Map<String, Object> toolInfo = tools.get(toolId);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("tool_id", toolId);
            detail.put("tool_name", valueOrDefault(toolInfo.get("tool_name"), ""));
            detail.put("tool_desc", valueOrDefault(toolInfo.get("tool_desc"), ""));
            if (needInputsOutputs) {
                detail.put("inputs", valueOrDefault(toolInfo.get("inputs_for_dl_gen"), List.of()));
                detail.put("outputs", valueOrDefault(toolInfo.get("outputs_for_dl_gen"), List.of()));
            }

            toolDetails.add(detail);
            retrievedPluginDict.put(pluginId, plugin);
            retrievedToolIdMap.put(toolId, pluginId);
        }

        return new RetrievedInfo(toolDetails, retrievedPluginDict, retrievedToolIdMap);
    }

    private static List<Map<String, Object>> formatParams(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> param : safeList(params)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", asString(param.get("name")));
            item.put("description", firstPresent(param, "description", "desc"));
            item.put("type", convertType(valueOrDefault(param.get("type"), 1)));
            result.add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> convertParams(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> param : safeList(params)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", asString(param.get("name")));
            item.put("desc", firstPresent(param, "desc", "description"));
            item.put("type", convertType(valueOrDefault(param.get("type"), 1)));
            result.add(item);
        }
        return result;
    }

    private static Object firstPresent(Map<String, Object> map, String first, String second) {
        Object value = map.get(first);
        return value != null ? value : valueOrDefault(map.get(second), "");
    }

    private static Object valueOrDefault(Object value, Object defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static List<String> safeStrings(List<String> values) {
        return values != null ? values : Collections.emptyList();
    }

    private static List<Map<String, Object>> safeList(List<Map<String, Object>> values) {
        return values != null ? values : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapsFrom(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapValues(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new ArrayList<>(((Map<String, Map<String, Object>>) map).values());
        }
        return List.of();
    }
}
