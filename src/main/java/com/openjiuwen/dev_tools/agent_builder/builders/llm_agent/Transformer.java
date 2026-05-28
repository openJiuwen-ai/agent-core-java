/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * DSL Transformer — transforms generated Agent configuration to standard DSL format.
 * <p>
 * Mirrors Python's {@code Transformer} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent.transformer}.
 */
public class Transformer {

    private static final long MS_PER_SECOND = 1000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Map<String, Object>> collectPlugin(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolIdMap
    ) {
        List<Map<String, Object>> collected = new ArrayList<>();
        for (String toolId : toolIdList) {
            if (!toolIdMap.containsKey(toolId)) {
                continue;
            }
            String pluginId = toolIdMap.get(toolId);
            Map<String, Object> plugin = pluginDict.getOrDefault(pluginId, Collections.emptyMap());
            @SuppressWarnings("unchecked")
            Map<String, Object> tools = (Map<String, Object>) plugin.getOrDefault("tools", Collections.emptyMap());
            Map<String, Object> tool = (Map<String, Object>) tools.getOrDefault(toolId, Collections.emptyMap());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("plugin_id", pluginId);
            entry.put("plugin_name", plugin.getOrDefault("plugin_name", ""));
            entry.put("tool_id", toolId);
            entry.put("tool_name", tool.getOrDefault("tool_name", ""));
            collected.add(entry);
        }
        return collected;
    }

    public static List<Map<String, Object>> collectWorkflow(
            List<String> workflowIdList,
            Map<String, Map<String, Object>> workflowDict
    ) {
        List<Map<String, Object>> collected = new ArrayList<>();
        for (String workflowId : workflowIdList) {
            Map<String, Object> workflow = workflowDict.getOrDefault(workflowId, Collections.emptyMap());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("workflow_id", workflowId);
            entry.put("workflow_name", workflow.get("workflow_name"));
            entry.put("workflow_version", workflow.get("workflow_version"));
            entry.put("description", workflow.get("workflow_desc"));
            collected.add(entry);
        }
        return collected;
    }

    public static List<Map<String, Object>> convertInputParameters(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (params == null) return result;
        for (Map<String, Object> p : params) {
            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("name", p.getOrDefault("name", ""));
            converted.put("desc", p.getOrDefault("desc", p.getOrDefault("description", "")));
            converted.put("type", p.getOrDefault("type", 1));
            converted.put("value", p.getOrDefault("value", ""));
            converted.put("method", p.getOrDefault("method", 0));
            converted.put("priority", p.getOrDefault("priority", 0));
            converted.put("is_runtime", p.getOrDefault("is_runtime", true));
            converted.put("is_required", p.getOrDefault("is_required", false));
            result.add(converted);
        }
        return result;
    }

    public static List<Map<String, Object>> convertOutputParameters(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (params == null) return result;
        for (Map<String, Object> p : params) {
            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("name", p.getOrDefault("name", ""));
            converted.put("desc", p.getOrDefault("desc", p.getOrDefault("description", "")));
            converted.put("type", p.getOrDefault("type", 1));
            converted.put("value", p.getOrDefault("value", ""));
            converted.put("method", p.getOrDefault("method", 0));
            converted.put("priority", p.getOrDefault("priority", 0));
            converted.put("is_runtime", p.getOrDefault("is_runtime", false));
            converted.put("is_required", p.getOrDefault("is_required", false));
            result.add(converted);
        }
        return result;
    }

    static Map<String, Object> convertToolToPlatform(
            Map<String, Object> tool, String pluginId, String pluginVersion, long currentTs
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", tool.getOrDefault("code", ""));
        result.put("language", tool.getOrDefault("language", "python"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputParams = (List<Map<String, Object>>) tool.getOrDefault("input_parameters", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputParams = (List<Map<String, Object>>) tool.getOrDefault("output_parameters", Collections.emptyList());
        result.put("request_params", convertInputParameters(inputParams));
        result.put("response_params", convertOutputParameters(outputParams));
        result.put("primary_id", null);
        result.put("tool_id", tool.getOrDefault("tool_id", ""));
        result.put("name", tool.getOrDefault("tool_name", tool.getOrDefault("name", "")));
        result.put("desc", tool.getOrDefault("desc", tool.getOrDefault("tool_desc", "")));
        result.put("space_id", "");
        result.put("plugin_id", pluginId);
        result.put("plugin_type", tool.get("language") != null ? 1 : 2);
        result.put("plugin_version", pluginVersion);
        result.put("input_parameters", convertInputParameters(inputParams));
        result.put("output_parameters", convertOutputParameters(outputParams));
        result.put("available", true);
        result.put("create_time", currentTs);
        result.put("update_time", currentTs);
        return result;
    }

    public static List<Map<String, Object>> buildPluginDependencies(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolIdMap,
            long currentTs
    ) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        Set<String> processedPluginIds = new HashSet<>();

        for (String toolId : toolIdList) {
            if (!toolIdMap.containsKey(toolId)) continue;
            String pluginId = toolIdMap.get(toolId);
            if (processedPluginIds.contains(pluginId)) continue;

            Map<String, Object> plugin = pluginDict.getOrDefault(pluginId, Collections.emptyMap());
            if (plugin.isEmpty()) continue;

            String pluginVersion = (String) plugin.getOrDefault("plugin_version", "draft");
            String pluginName = (String) plugin.getOrDefault("plugin_name", "");
            String pluginDesc = (String) plugin.getOrDefault("plugin_desc", "");

            List<Map<String, Object>> toolList = new ArrayList<>();
            Object toolsObj = plugin.get("tools");
            if (toolsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tools = (Map<String, Object>) toolsObj;
                for (Map.Entry<String, Object> e : tools.entrySet()) {
                    if (e.getKey().equals(toolId)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> t = (Map<String, Object>) e.getValue();
                        toolList.add(convertToolToPlatform(t, pluginId, pluginVersion, currentTs));
                    }
                }
            }

            Map<String, Object> dep = new LinkedHashMap<>();
            dep.put("plugin_id", pluginId);
            dep.put("plugin_version", pluginVersion);
            dep.put("primary_id", null);
            dep.put("name", pluginName);
            dep.put("desc", pluginDesc);
            dep.put("desc_mk", "");
            dep.put("url", "");
            dep.put("space_id", "");
            dep.put("icon_uri", "");
            dep.put("plugin_type", 2);
            dep.put("tools", null);
            dep.put("inputs", new ArrayList<>());
            dep.put("create_time", currentTs);
            dep.put("update_time", currentTs);
            dep.put("tool_list", toolList);

            dependencies.add(dep);
            processedPluginIds.add(pluginId);
        }

        return dependencies;
    }

    public static List<Map<String, Object>> buildWorkflowDependencies(
            List<String> workflowIdList,
            Map<String, Map<String, Object>> workflowDict,
            long currentTs
    ) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (String workflowId : workflowIdList) {
            Map<String, Object> workflow = workflowDict.getOrDefault(workflowId, Collections.emptyMap());
            Map<String, Object> dep = new LinkedHashMap<>();
            dep.put("workflow_id", workflowId);
            dep.put("workflow_version", workflow.getOrDefault("workflow_version", "draft"));
            dep.put("primary_id", null);
            dep.put("name", workflow.getOrDefault("workflow_name", ""));
            dep.put("desc", workflow.getOrDefault("workflow_desc", ""));
            dep.put("space_id", "");
            dep.put("url", "template");
            dep.put("icon_uri", "");
            dep.put("schema", "");
            dep.put("input_parameters", workflow.getOrDefault("input_parameters", new ArrayList<>()));
            dep.put("output_parameters", workflow.getOrDefault("output_parameters", new ArrayList<>()));
            dep.put("create_time", currentTs);
            dep.put("update_time", currentTs);
            dependencies.add(dep);
        }
        return dependencies;
    }

    @SuppressWarnings("unchecked")
    public String transformToDsl(Map<String, Object> agentInfo, Map<String, Object> resource) {
        long nowMs = Instant.now().toEpochMilli();

        Map<String, Object> dsl = LlmAgentTemplate.deepCopy(LlmAgentTemplate.create());
        dsl.put("agent_id", UUID.randomUUID().toString());
        dsl.put("name", agentInfo.getOrDefault("name", ""));
        dsl.put("description", agentInfo.getOrDefault("description", ""));

        Map<String, Object> configs = (Map<String, Object>) dsl.get("configs");
        configs.put("system_prompt", agentInfo.getOrDefault("prompt", ""));
        dsl.put("opening_remarks", agentInfo.getOrDefault("opening_remarks", ""));

        Object pluginIdListObj = agentInfo.getOrDefault("plugin", Collections.emptyList());
        List<String> pluginIdList = pluginIdListObj instanceof List ? (List<String>) pluginIdListObj : Collections.emptyList();

        Map<String, Map<String, Object>> pluginDict = (Map<String, Map<String, Object>>) resource.getOrDefault("plugin_dict", Collections.emptyMap());
        Map<String, String> toolIdMap = (Map<String, String>) resource.getOrDefault("tool_id_map", Collections.emptyMap());

        if (!pluginIdList.isEmpty()) {
            dsl.put("plugins", collectPlugin(pluginIdList, pluginDict, toolIdMap));
        }

        Object workflowIdListObj = agentInfo.getOrDefault("workflow", Collections.emptyList());
        List<String> workflowIdList = workflowIdListObj instanceof List ? (List<String>) workflowIdListObj : Collections.emptyList();

        Map<String, Map<String, Object>> workflowDict = (Map<String, Map<String, Object>>) resource.getOrDefault("workflow_dict", Collections.emptyMap());

        if (!workflowIdList.isEmpty()) {
            dsl.put("workflows", collectWorkflow(workflowIdList, workflowDict));
        }

        dsl.put("create_time", nowMs);
        dsl.put("update_time", nowMs);

        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("plugins", buildPluginDependencies(pluginIdList, pluginDict, toolIdMap, nowMs));
        dependencies.put("workflows", buildWorkflowDependencies(workflowIdList, workflowDict, nowMs));
        dependencies.put("knowledge_bases", new ArrayList<>());
        dependencies.put("prompt_templates", new ArrayList<>());

        dsl.put("dependencies", dependencies);

        try {
            return MAPPER.writeValueAsString(dsl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DSL", e);
        }
    }
}
