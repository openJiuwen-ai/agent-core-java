/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Resource retriever for agent builder.
 * <p>
 * Mirrors Python's {@code ResourceRetriever} in
 * {@code openjiuwen.dev_tools.agent_builder.resource.retriever}.
 */
public class ResourceRetriever {

    private final Object llm;
    private final Map<String, Map<String, Object>> pluginDict;
    private final Map<String, String> toolPluginIdMap;

    public ResourceRetriever(Object llm) {
        this.llm = llm;
        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(loadResources());
        this.pluginDict = result.pluginDict();
        this.toolPluginIdMap = result.toolPluginIdMap();
    }

    public ResourceRetriever(Object llm, List<Map<String, Object>> rawPlugins) {
        this.llm = llm;
        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);
        this.pluginDict = result.pluginDict();
        this.toolPluginIdMap = result.toolPluginIdMap();
    }

    public Object getLlm() {
        return llm;
    }

    public Map<String, Map<String, Object>> getPluginDict() {
        return Collections.unmodifiableMap(pluginDict);
    }

    public Map<String, String> getToolPluginIdMap() {
        return Collections.unmodifiableMap(toolPluginIdMap);
    }

    public static List<Map<String, Object>> loadResources() {
        return loadResources(null);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadResources(String sourcePath) {
        Path pluginJson = sourcePath != null
                ? Path.of(sourcePath)
                : Path.of("src", "main", "java", "com", "openjiuwen",
                        "dev_tools", "agent_builder", "resource", "plugins.json");
        if (!Files.exists(pluginJson)) {
            return List.of();
        }
        try {
            Map<String, Object> data = JsonUtils.getMapper().readValue(
                    Files.readString(pluginJson), new TypeReference<>() {
                    });
            Object plugins = data.get("plugins");
            return plugins instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load resources from " + pluginJson, e);
        }
    }

    /** Retrieve resources based on query. */
    public Map<String, Object> retrieve(Map<String, Object> query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", Collections.emptyList());
        result.put("documents", Collections.emptyList());
        result.put("examples", Collections.emptyList());
        return result;
    }

    public Map<String, Object> retrieve(List<Map<String, Object>> dialogHistory) {
        return retrieve(dialogHistory, true);
    }

    public Map<String, Object> retrieve(List<Map<String, Object>> dialogHistory, boolean forWorkflow) {
        String dialogHistoryQuery = formatDialogHistory(dialogHistory);
        List<Map<String, Object>> pluginInfoList = PluginProcessor.formatForPrompt(pluginDict);
        List<BaseMessage> messages = Prompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                "dialog_history", dialogHistoryQuery,
                "plugin_info_list", String.valueOf(pluginInfoList))).toMessages();

        Map<String, Object> data = llmRetrieve(messages);
        List<String> toolIdList = stringsFrom(data.get("tool_id_list"));
        PluginProcessor.RetrievedInfo retrievedInfo = PluginProcessor.getRetrievedInfo(
                toolIdList, pluginDict, toolPluginIdMap, forWorkflow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugins", retrievedInfo.toolList());
        result.put("plugin_dict", retrievedInfo.retrievedPluginDict());
        result.put("tool_id_map", retrievedInfo.retrievedToolIdMap());
        return result;
    }

    protected Map<String, Object> llmRetrieve(List<BaseMessage> messages) {
        return Map.of("tool_id_list", List.of());
    }

    private static String formatDialogHistory(List<Map<String, Object>> dialogHistory) {
        if (dialogHistory == null || dialogHistory.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> message : dialogHistory) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(String.valueOf(message.getOrDefault("role", "")))
                    .append(": ")
                    .append(String.valueOf(message.getOrDefault("content", "")));
        }
        return builder.toString();
    }

    private static List<String> stringsFrom(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }
}
