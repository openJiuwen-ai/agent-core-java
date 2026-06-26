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

        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        cnSchema.put("properties", Map.of(
                "server_id", Map.of(
                        "type", "string",
                        "description", "MCP 服务器的 server_id"
                )
        ));
        cnSchema.put("required", Arrays.asList("server_id"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        enSchema.put("properties", Map.of(
                "server_id", Map.of(
                        "type", "string",
                        "description", "The server_id of the MCP server"
                )
        ));
        enSchema.put("required", Arrays.asList("server_id"));
        INPUT_PARAMS.put("en", enSchema);
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
}
