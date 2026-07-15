/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Message conversion and entity update helpers for graph memory.
 * 
 * @since 0.1.7
 */
public final class GraphMemoryUtils {
    /**
     * GraphMemoryUtils.
     * 
     * @since 0.1.7
     */
    private GraphMemoryUtils() {
    }

    /**
     * msg2dict.
     * 
     * @param messages messages
     * @param isPreserveMeta isPreserveMeta
     * @return the result
     * @since 0.1.7
     */
    public static List<Map<String, Object>> msg2dict(List<?> messages, boolean isPreserveMeta) {
        if (messages == null) {
            throw new IllegalArgumentException("Input is not a list of dict or BaseMessage");
        }
        List<?> list = messages;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object message : list) {
            if (message instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) map);
                result.add(typed);
            } else if (message instanceof BaseMessage baseMessage) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", baseMessage.getRole());
                item.put("content", baseMessage.getContent());
                if (isPreserveMeta) {
                    item.put("name", baseMessage.getName());
                    item.put("metadata", baseMessage.getMetadata());
                }
                result.add(item);
            } else {
                throw new IllegalArgumentException("Input is not a list of dict or BaseMessage");
            }
        }
        return result;
    }

    /**
     * updateEntity.
     * 
     * @param entity entity
     * @param response response
     * @param extractionSchema extractionSchema
     * @since 0.1.7
     */
    public static void updateEntity(Entity entity, String response, Map<String, Object> extractionSchema) {
        Object extracted = ParseResponse.parseJson(response, extractionSchema);
        if (extracted == null) {
            extracted = new LinkedHashMap<String, Object>();
        }
        if (extracted instanceof List<?> list && !list.isEmpty()) {
            extracted = list.get(0);
        }
        if (extracted instanceof String stringValue) {
            extracted = Map.of("summary", stringValue);
        }
        if (extracted instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            parseSummary(entity, typed);
            parseAttributes(entity, typed);
        }
    }

    /**
     * parseSummary.
     * 
     * @param entity entity
     * @param extractedEntityInfo extractedEntityInfo
     * @since 0.1.7
     */
    public static void parseSummary(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object summary = extractedEntityInfo.getOrDefault("summary", "");
        String isResolved;
        if (summary instanceof List<?> list) {
            isResolved =
                list.stream().map(item -> String.valueOf(item).trim()).reduce((a, b) -> a + "\n" + b).orElse("");
        } else if (summary == null) {
            isResolved = "";
        } else {
            isResolved = String.valueOf(summary);
        }
        isResolved = isResolved.trim();
        String folded = isResolved.toLowerCase(Locale.ROOT);
        if (!isResolved.isBlank() && !folded.contains("null") && !folded.contains("none")
                && !folded.contains("empty")) {
            entity.setContent(isResolved);
        }
    }

    /**
     * parseAttributes.
     * 
     * @param entity entity
     * @param extractedEntityInfo extractedEntityInfo
     * @since 0.1.7
     */
    public static void parseAttributes(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object attributes = extractedEntityInfo.get("attributes");
        Object parsed = attributes;
        if (attributes instanceof String stringValue) {
            parsed = ParseResponse.parseJson(stringValue, null);
        } else if (attributes instanceof List<?> list) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Object item : list) {
                if (item instanceof Map.Entry<?, ?> entry) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            parsed = converted;
        }
        if (parsed instanceof Map<?, ?> map && !map.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) map);
            entity.setAttributes(typed);
        }
    }

    /**
     * assembleInvokeParams.
     * 
     * @param kwargs kwargs
     * @param template template
     * @param outputModel outputModel
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> assembleInvokeParams(Map<String, Object> kwargs, PromptTemplate template,
            Map<String, Object> outputModel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("messages", msg2dict(template.format(kwargs).toMessages(), false));
        if (outputModel != null) {
            params.put("response_format", outputModel);
        }
        return params;
    }
}
