/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.sdk.OfficialMcpClientFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP callable helper and SearchFunds tool metadata used by tool-call tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.callable_fortest}.</p>
 */
public final class CallableForTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String MCP_URL = System.getenv().getOrDefault("MCP_URL", "");
    public static final String MCP_NAME = System.getenv().getOrDefault("MCP_NAME", "Streamable HTTP Python Server");
    public static final String DESCRIPTION = buildSearchFundsDescription();
    public static final Map<String, Object> TOOL = Map.of("name", "SearchFunds", "description", DESCRIPTION);

    private CallableForTest() {
    }

    @FunctionalInterface
    public interface McpToolCaller {
        Object call(Map<String, Object> toolArguments) throws Exception;
    }

    @FunctionalInterface
    public interface McpClientFactory {
        McpClient create(McpServerConfig config);
    }

    public static McpToolCaller makeSyncMcpCaller(String url) {
        return makeSyncMcpCaller(url, MCP_NAME);
    }

    public static McpToolCaller makeSyncMcpCaller(String url, String name) {
        return makeSyncMcpCaller(url, name, OfficialMcpClientFactory::create);
    }

    static McpToolCaller makeSyncMcpCaller(String url, String name, McpClientFactory clientFactory) {
        return toolArguments -> {
            String toolName = String.valueOf(toolArguments.get("name"));
            Map<String, Object> arguments = normalizeArguments(toolArguments.get("arguments"));
            McpServerConfig config = McpServerConfig.builder()
                    .serverName(name)
                    .serverPath(url)
                    .clientType("sse")
                    .build();
            McpClient client = clientFactory.create(config);
            client.connect();
            try {
                return extractText(client.callTool(toolName, arguments));
            } finally {
                client.disconnect();
            }
        };
    }

    public static Map<String, Object> getTool() {
        return TOOL;
    }

    public static String getDescription() {
        return DESCRIPTION;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeArguments(Object rawArguments) {
        if (rawArguments == null) {
            return Map.of();
        }
        if (rawArguments instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (rawArguments instanceof String text) {
            try {
                Object parsed = MAPPER.readValue(text, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    return normalizeArguments(map);
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException(
                        "Failed to parse `arguments` as JSON string. Raw arguments: " + text,
                        exception);
            }
        }
        throw new IllegalArgumentException("`arguments` must be a JSON object or a map");
    }

    private static Object extractText(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
            Object content = map.get("content");
            if (content instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> item && item.get("text") != null) {
                    return String.valueOf(item.get("text"));
                }
            }
        }
        return result;
    }

    private static String buildSearchFundsDescription() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "SearchFunds");
        function.put("description", "Search funds by name, code, category, status, return, quota, and fee fields.");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("category", property("string", "Fund category"));
        properties.put("keyword", property("string", "Fund name keyword"));
        properties.put("size", property("number", "Page size"));
        properties.put("sortOrder", property("string", "Sort order"));
        properties.put("tradeStatus", property("string", "Trading status"));
        properties.put("sortColumn", property("string", "Sort column"));
        properties.put("page", property("number", "Page number starting from 0"));

        parameters.put("properties", properties);
        function.put("parameters", parameters);
        schema.put("function", function);
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize SearchFunds schema", exception);
        }
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
