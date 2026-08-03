/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.security.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DSL transformer for LLM agents.
 *
 * <p>Mirrors Python's {@code Transformer} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/transformer.py}.</p>
 */
public class Transformer {

    private static final long MS_PER_SECOND = 1000L;

    public static List<Map<String, Object>> collectPlugin(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolIdMap) {
        List<Map<String, Object>> collected = new ArrayList<>();
        for (String toolId : toolIdList) {
            if (!toolIdMap.containsKey(toolId)) {
                continue;
            }

            String pluginId = toolIdMap.get(toolId);
            Map<String, Object> plugin = pluginDict.getOrDefault(pluginId, Collections.emptyMap());
            @SuppressWarnings("unchecked")
            Map<String, Object> tools = (Map<String, Object>) plugin.getOrDefault("tools", Collections.emptyMap());
            @SuppressWarnings("unchecked")
            Map<String, Object> tool = (Map<String, Object>) tools.getOrDefault(toolId, Collections.emptyMap());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("plugin_id", pluginId);
            item.put("plugin_name", plugin.getOrDefault("plugin_name", ""));
            item.put("tool_id", toolId);
            item.put("tool_name", tool.getOrDefault("tool_name", ""));
            collected.add(item);
        }
        return collected;
    }

    public static List<Map<String, Object>> collectWorkflow(
            List<String> workflowIdList,
            Map<String, Map<String, Object>> workflowDict) {
        List<Map<String, Object>> collected = new ArrayList<>();
        for (String workflowId : workflowIdList) {
            Map<String, Object> workflow = workflowDict.getOrDefault(workflowId, Collections.emptyMap());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflow_id", workflowId);
            item.put("workflow_name", workflow.get("workflow_name"));
            item.put("workflow_version", workflow.get("workflow_version"));
            item.put("description", workflow.get("workflow_desc"));
            collected.add(item);
        }
        return collected;
    }

    public static List<Map<String, Object>> convertInputParameters(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> param : params == null ? List.<Map<String, Object>>of() : params) {
            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("name", param.getOrDefault("name", ""));
            converted.put("desc", param.getOrDefault("desc", param.getOrDefault("description", "")));
            converted.put("type", param.getOrDefault("type", 1));
            converted.put("value", param.getOrDefault("value", ""));
            converted.put("method", param.getOrDefault("method", 0));
            converted.put("priority", param.getOrDefault("priority", 0));
            converted.put("is_runtime", param.getOrDefault("is_runtime", true));
            converted.put("is_required", param.getOrDefault("is_required", false));
            result.add(converted);
        }
        return result;
    }

    public static List<Map<String, Object>> convertOutputParameters(List<Map<String, Object>> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> param : params == null ? List.<Map<String, Object>>of() : params) {
            Map<String, Object> converted = new LinkedHashMap<>();
            converted.put("name", param.getOrDefault("name", ""));
            converted.put("desc", param.getOrDefault("desc", param.getOrDefault("description", "")));
            converted.put("type", param.getOrDefault("type", 1));
            converted.put("value", param.getOrDefault("value", ""));
            converted.put("method", param.getOrDefault("method", 0));
            converted.put("priority", param.getOrDefault("priority", 0));
            converted.put("is_runtime", param.getOrDefault("is_runtime", false));
            converted.put("is_required", param.getOrDefault("is_required", false));
            result.add(converted);
        }
        return result;
    }

    static Map<String, Object> convertToolToPlatform(
            Map<String, Object> tool,
            String pluginId,
            String pluginVersion,
            long currentTs) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputParameters =
                (List<Map<String, Object>>) tool.getOrDefault("input_parameters", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputParameters =
                (List<Map<String, Object>>) tool.getOrDefault("output_parameters", Collections.emptyList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", tool.getOrDefault("code", ""));
        result.put("language", tool.getOrDefault("language", "python"));
        result.put("request_params", convertInputParameters(inputParameters));
        result.put("response_params", convertOutputParameters(outputParameters));
        result.put("primary_id", null);
        result.put("tool_id", tool.getOrDefault("tool_id", ""));
        result.put("name", tool.getOrDefault("tool_name", tool.getOrDefault("name", "")));
        result.put("desc", tool.getOrDefault("desc", tool.getOrDefault("tool_desc", "")));
        result.put("space_id", "");
        result.put("plugin_id", pluginId);
        result.put("plugin_type", tool.get("language") != null ? 1 : 2);
        result.put("plugin_version", pluginVersion);
        result.put("input_parameters", convertInputParameters(inputParameters));
        result.put("output_parameters", convertOutputParameters(outputParameters));
        result.put("available", true);
        result.put("create_time", currentTs);
        result.put("update_time", currentTs);
        return result;
    }

    public static List<Map<String, Object>> buildPluginDependencies(
            List<String> toolIdList,
            Map<String, Map<String, Object>> pluginDict,
            Map<String, String> toolIdMap,
            long currentTs) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        Set<String> processedPluginIds = new HashSet<>();

        for (String toolId : toolIdList) {
            if (!toolIdMap.containsKey(toolId)) {
                continue;
            }
            String pluginId = toolIdMap.get(toolId);
            if (processedPluginIds.contains(pluginId)) {
                continue;
            }

            Map<String, Object> plugin = pluginDict.getOrDefault(pluginId, Collections.emptyMap());
            if (plugin.isEmpty()) {
                continue;
            }

            String pluginVersion = String.valueOf(plugin.getOrDefault("plugin_version", "draft"));
            String pluginName = String.valueOf(plugin.getOrDefault("plugin_name", ""));
            String pluginDesc = String.valueOf(plugin.getOrDefault("plugin_desc", ""));

            List<Map<String, Object>> toolList = new ArrayList<>();
            Object toolsObject = plugin.get("tools");
            if (toolsObject instanceof Map<?, ?> tools) {
                Object toolObject = tools.get(toolId);
                if (toolObject instanceof Map<?, ?> toolMap) {
                    toolList.add(convertToolToPlatform(toStringObjectMap(toolMap), pluginId, pluginVersion, currentTs));
                }
            }

            Map<String, Object> dependency = new LinkedHashMap<>();
            dependency.put("plugin_id", pluginId);
            dependency.put("plugin_version", pluginVersion);
            dependency.put("primary_id", null);
            dependency.put("name", pluginName);
            dependency.put("desc", pluginDesc);
            dependency.put("desc_mk", "");
            dependency.put("url", "");
            dependency.put("space_id", "");
            dependency.put("icon_uri", "");
            dependency.put("plugin_type", 2);
            dependency.put("tools", null);
            dependency.put("inputs", new ArrayList<>());
            dependency.put("create_time", currentTs);
            dependency.put("update_time", currentTs);
            dependency.put("tool_list", toolList);
            dependencies.add(dependency);

            processedPluginIds.add(pluginId);
        }

        return dependencies;
    }

    public static List<Map<String, Object>> buildWorkflowDependencies(
            List<String> workflowIdList,
            Map<String, Map<String, Object>> workflowDict,
            long currentTs) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (String workflowId : workflowIdList) {
            Map<String, Object> workflow = workflowDict.getOrDefault(workflowId, Collections.emptyMap());
            Map<String, Object> dependency = new LinkedHashMap<>();
            dependency.put("workflow_id", workflowId);
            dependency.put("workflow_version", workflow.getOrDefault("workflow_version", "draft"));
            dependency.put("primary_id", null);
            dependency.put("name", workflow.getOrDefault("workflow_name", ""));
            dependency.put("desc", workflow.getOrDefault("workflow_desc", ""));
            dependency.put("space_id", "");
            dependency.put("url", "template");
            dependency.put("icon_uri", "");
            dependency.put("schema", "");
            dependency.put("input_parameters", workflow.getOrDefault("input_parameters", new ArrayList<>()));
            dependency.put("output_parameters", workflow.getOrDefault("output_parameters", new ArrayList<>()));
            dependency.put("create_time", currentTs);
            dependency.put("update_time", currentTs);
            dependencies.add(dependency);
        }
        return dependencies;
    }

    @SuppressWarnings("unchecked")
    public String transformToDsl(Map<String, Object> agentInfo, Map<String, Object> resource) {
        long nowMsTimestamp = Instant.now().getEpochSecond() * MS_PER_SECOND;

        Map<String, Object> dsl = LlmAgentTemplate.deepCopy(LlmAgentTemplate.create());
        dsl.put("agent_id", UUID.randomUUID().toString());
        dsl.put("name", agentInfo.getOrDefault("name", ""));
        dsl.put("description", agentInfo.getOrDefault("description", ""));
        ((Map<String, Object>) dsl.get("configs")).put("system_prompt", agentInfo.getOrDefault("prompt", ""));
        dsl.put("opening_remarks", agentInfo.getOrDefault("opening_remarks", ""));

        Object pluginIdListObject = agentInfo.getOrDefault("plugin", Collections.emptyList());
        List<String> pluginIdList = pluginIdListObject instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (!pluginIdList.isEmpty()) {
            dsl.put(
                    "plugins",
                    collectPlugin(
                            pluginIdList,
                            (Map<String, Map<String, Object>>) resource.getOrDefault("plugin_dict", Collections.emptyMap()),
                            (Map<String, String>) resource.getOrDefault("tool_id_map", Collections.emptyMap())
                    )
            );
        }

        Object workflowIdListObject = agentInfo.getOrDefault("workflow", Collections.emptyList());
        List<String> workflowIdList = workflowIdListObject instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (!workflowIdList.isEmpty()) {
            dsl.put(
                    "workflows",
                    collectWorkflow(
                            workflowIdList,
                            (Map<String, Map<String, Object>>) resource.getOrDefault("workflow_dict", Collections.emptyMap())
                    )
            );
        }

        dsl.put("create_time", nowMsTimestamp);
        dsl.put("update_time", nowMsTimestamp);

        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put(
                "plugins",
                buildPluginDependencies(
                        pluginIdList,
                        (Map<String, Map<String, Object>>) resource.getOrDefault("plugin_dict", Collections.emptyMap()),
                        (Map<String, String>) resource.getOrDefault("tool_id_map", Collections.emptyMap()),
                        nowMsTimestamp
                )
        );
        dependencies.put(
                "workflows",
                buildWorkflowDependencies(
                        workflowIdList,
                        (Map<String, Map<String, Object>>) resource.getOrDefault("workflow_dict", Collections.emptyMap()),
                        nowMsTimestamp
                )
        );
        dependencies.put("knowledge_bases", new ArrayList<>());
        dependencies.put("prompt_templates", new ArrayList<>());
        dsl.put("dependencies", dependencies);

        return JsonUtils.safeJsonDumps(dsl);
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }
}
