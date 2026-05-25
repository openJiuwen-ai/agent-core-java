/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for list_mcp_resources tool.
 * <p>
 * Mirrors Python's {@code ListMcpResourcesMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.mcp}.
 */
public class ListMcpResourcesMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "列出指定 MCP 服务器上可用的资源列表。");
        DESCRIPTIONS.put("en", "List available resources exposed by the specified MCP server.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("server_id", Map.of("type", "string", "description", "MCP 服务器的 server_id"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("server_id"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("server_id", Map.of("type", "string", "description", "The server_id of the MCP server"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("server_id"));
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