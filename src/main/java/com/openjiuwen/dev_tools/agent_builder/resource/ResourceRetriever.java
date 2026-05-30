/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.security.JsonUtils;

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
}
