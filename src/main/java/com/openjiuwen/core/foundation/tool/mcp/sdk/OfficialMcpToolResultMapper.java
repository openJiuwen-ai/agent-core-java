/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Maps official SDK tool results to the stable runtime contract used by upper layers.
 */
public final class OfficialMcpToolResultMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OfficialMcpToolResultMapper() {
    }

    public static Map<String, Object> map(String toolName, McpSchema.CallToolResult result) {
        List<Map<String, Object>> content = mapContent(result.content());
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("tool_name", toolName);
        normalized.put("text", extractText(content));
        normalized.put("content", content);
        normalized.put("structured_content", normalizeStructuredContent(result.structuredContent()));
        normalized.put("is_error", Boolean.TRUE.equals(result.isError()));
        return normalized;
    }

    private static List<Map<String, Object>> mapContent(List<McpSchema.Content> contentItems) {
        if (contentItems == null || contentItems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpSchema.Content contentItem : contentItems) {
            result.add(convertContent(contentItem));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> convertContent(McpSchema.Content contentItem) {
        Object converted = OBJECT_MAPPER.convertValue(contentItem, new TypeReference<Object>() {
        });
        if (converted instanceof Map<?, ?> convertedMap) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : convertedMap.entrySet()) {
                item.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            item.putIfAbsent("type", contentItem.type());
            return item;
        }
        return Map.of("type", contentItem.type());
    }

    private static String extractText(List<Map<String, Object>> contentItems) {
        if (contentItems.isEmpty()) {
            return "";
        }
        StringJoiner textJoiner = new StringJoiner("\n");
        for (Map<String, Object> contentItem : contentItems) {
            if ("text".equals(contentItem.get("type")) && contentItem.get("text") != null) {
                textJoiner.add(String.valueOf(contentItem.get("text")));
            }
        }
        return textJoiner.toString();
    }

    private static Object normalizeStructuredContent(Object structuredContent) {
        if (structuredContent == null) {
            return Map.of();
        }
        Object converted = OBJECT_MAPPER.convertValue(structuredContent, new TypeReference<Object>() {
        });
        if (converted instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return converted;
    }
}
