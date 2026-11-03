/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Intelligently retrieves workflow resources from dialog history.
 *
 * <p>Mirrors Python's {@code ResourceRetriever} in
 * {@code openjiuwen/dev_tools/agent_builder/resource/retriever.py}.</p>
 */
public class ResourceRetriever {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final String DEFAULT_RESOURCE_PATH = "openjiuwen/dev_tools/agent_builder/resource/plugins.json";

    private final Model llm;
    private final Map<String, Map<String, Object>> pluginDict;
    private final Map<String, String> toolPluginIdMap;

    public ResourceRetriever(Model llm) {
        this(llm, loadResources());
    }

    ResourceRetriever(Model llm, List<Map<String, Object>> rawPlugins) {
        this.llm = Objects.requireNonNull(llm, "llm");
        PluginProcessor.PreprocessResult result = PluginProcessor.preprocess(rawPlugins);
        this.pluginDict = result.pluginDict();
        this.toolPluginIdMap = result.toolPluginIdMap();
        LOGGER.debug("Resource retriever initialized, plugin_count={}, tool_count={}",
                pluginDict.size(), toolPluginIdMap.size());
    }

    public Model getLlm() {
        return llm;
    }

    public Map<String, Map<String, Object>> getPluginDict() {
        return pluginDict;
    }

    public Map<String, String> getToolPluginIdMap() {
        return toolPluginIdMap;
    }

    public static List<Map<String, Object>> loadResources() {
        return loadResources(null);
    }

    public static List<Map<String, Object>> loadResources(String sourcePath) {
        Map<String, Object> data;
        if (sourcePath == null || sourcePath.isBlank()) {
            data = loadDefaultResource();
        } else {
            Path pluginJson = Path.of(sourcePath);
            if (!Files.exists(pluginJson)) {
                LOGGER.warning("Plugin config file not found, using empty list: {}", pluginJson);
                return List.of();
            }
            try {
                data = AgentBuilderUtils.loadJsonFile(pluginJson.toString());
            } catch (java.io.FileNotFoundException exception) {
                LOGGER.warning("Plugin config file not found, using empty list: {}", pluginJson);
                return List.of();
            }
        }
        return mapList(data.get("plugins"));
    }

    public Map<String, Object> retrieve(List<Map<String, ?>> dialogHistory) {
        return retrieve(dialogHistory, true);
    }

    public Map<String, Object> retrieve(List<Map<String, ?>> dialogHistory, boolean forWorkflow) {
        try {
            String dialogHistoryQuery = AgentBuilderUtils.formatDialogHistory(dialogHistory);
            List<Map<String, Object>> pluginInfoList = PluginProcessor.formatForPrompt(pluginDict);

            List<BaseMessage> messages = AgentBuilderResourcePrompt.RETRIEVE_SYSTEM_TEMPLATE.format(Map.of(
                    "dialog_history", dialogHistoryQuery,
                    "plugin_info_list", String.valueOf(pluginInfoList)
            )).toMessages();

            Map<String, Object> data = llmRetrieve(messages);
            List<String> toolIdList = stringList(data.get("tool_id_list"));
            PluginProcessor.RetrievedInfo retrievedInfo = PluginProcessor.getRetrievedInfo(
                    toolIdList,
                    pluginDict,
                    toolPluginIdMap,
                    forWorkflow
            );

            LOGGER.info("Resource retrieval completed, plugin_count={}, for_workflow={}",
                    retrievedInfo.toolList().size(), forWorkflow);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plugins", retrievedInfo.toolList());
            result.put("plugin_dict", retrievedInfo.retrievedPluginDict());
            result.put("tool_id_map", retrievedInfo.retrievedToolIdMap());
            return result;
        } catch (Exception exception) {
            String errorMessage = "Resource retrieval failed: " + exceptionMessage(exception);
            LOGGER.error("Resource retrieval failed, error={}, for_workflow={}",
                    exceptionMessage(exception), forWorkflow);
            throw new ExecutionError(
                    StatusCode.AGENT_BUILDER_RESOURCE_RETRIEVE_ERROR,
                    errorMessage,
                    Map.of("for_workflow", forWorkflow),
                    exception,
                    Map.of("reason", errorMessage)
            );
        }
    }

    Map<String, Object> llmRetrieve(List<BaseMessage> prompts) {
        try {
            AssistantMessage response = llm.invoke(prompts).toCompletableFuture().join();
            String jsonText = AgentBuilderUtils.extractJsonFromText(
                    Objects.toString(response == null ? null : response.getContent(), "")
            );
            Object data = JsonUtils.safeJsonLoads(jsonText, new LinkedHashMap<String, Object>());
            if (!(data instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("LLM returned format error, expected dict, got: "
                        + (data == null ? "null" : data.getClass()));
            }
            return toStringObjectMap(map);
        } catch (Exception exception) {
            String errorMessage = "LLM retrieval call failed: " + exceptionMessage(exception);
            LOGGER.error("LLM retrieval call failed, error={}", exceptionMessage(exception));
            throw new ValidationError(
                    StatusCode.RESOURCE_VALUE_INVALID,
                    errorMessage,
                    Map.of("error", exceptionMessage(exception)),
                    exception,
                    Map.of("resource_type", "resource", "reason", errorMessage)
            );
        }
    }

    private static Map<String, Object> loadDefaultResource() {
        ClassLoader classLoader = ResourceRetriever.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (inputStream == null) {
                LOGGER.warning("Plugin config file not found, using empty list: {}", DEFAULT_RESOURCE_PATH);
                return new LinkedHashMap<>();
            }
            String raw = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Object data = JsonUtils.safeJsonLoads(raw, new LinkedHashMap<String, Object>());
            if (data instanceof Map<?, ?> map) {
                return toStringObjectMap(map);
            }
            return new LinkedHashMap<>();
        } catch (IOException exception) {
            String errorMessage = "JSON parse error: " + exception.getMessage();
            throw new ValidationError(
                    StatusCode.CONTEXT_MESSAGE_INVALID,
                    errorMessage,
                    Map.of("file_path", DEFAULT_RESOURCE_PATH, "error", exception.getMessage()),
                    exception,
                    Map.of("error_msg", errorMessage)
            );
        }
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toStringObjectMap(map));
            }
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }
}
