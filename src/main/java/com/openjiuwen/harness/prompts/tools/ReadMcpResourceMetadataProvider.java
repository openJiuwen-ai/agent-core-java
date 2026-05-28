/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual descriptions and input params for read_mcp_resource tool.
 * <p>
 * Mirrors Python's {@code ReadMcpResourceMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.mcp}.
 */
public class ReadMcpResourceMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "读取指定 MCP 服务器上某个资源的内容。");
        DESCRIPTIONS.put("en", "Read the content of a specific resource from the specified MCP server.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("server_id", Map.of("type", "string", "description", "MCP 服务器的 server_id"));
        cnProps.put("uri", Map.of("type", "string", "description", "要读取的资源 URI"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Arrays.asList("server_id", "uri"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("server_id", Map.of("type", "string", "description", "The server_id of the MCP server"));
        enProps.put("uri", Map.of("type", "string", "description", "The URI of the resource to read"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Arrays.asList("server_id", "uri"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "read_mcp_resource";
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