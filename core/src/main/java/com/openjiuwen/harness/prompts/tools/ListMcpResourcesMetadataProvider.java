/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ListMcpResourcesMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/mcp.py}.
 */
public final class ListMcpResourcesMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "列出指定 MCP 服务器上可用的资源列表。");
        DESCRIPTIONS.put("en", "List available resources exposed by the specified MCP server.");

        INPUT_PARAMS.put("cn", schema(
                "MCP 服务器的 server_id",
                "MCP 服务器的 server_name；未提供 server_id 时按名称解析"));
        INPUT_PARAMS.put("en", schema(
                "The server_id of the MCP server",
                "The server_name of the MCP server; used when server_id is omitted"));
    }

    @Override
    public String getName() {
        return "list_mcp_resources";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }

    private static Map<String, Object> schema(String serverIdDescription, String serverNameDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server_id", Map.of("type", "string", "description", serverIdDescription));
        properties.put("server_name", Map.of("type", "string", "description", serverNameDescription));

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", Arrays.asList("server_id"));
        return inputSchema;
    }
}
