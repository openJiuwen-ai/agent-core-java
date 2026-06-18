/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a single MCP resource by URI.
 *
 * <p>Mirrors Python's {@code ReadMcpResourceTool} in
 * {@code openjiuwen/harness/tools/mcp_tools.py}.</p>
 */
public class ReadMcpResourceTool extends AbstractHarnessTool {

    private final McpResourceReader resourceReader;

    public ReadMcpResourceTool(McpResourceReader resourceReader) {
        super(toolCard("read_mcp_resource", "ReadMcpResourceTool", "Read an MCP resource by URI."));
        this.resourceReader = resourceReader;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String serverId = stringValue(inputs == null ? null : inputs.get("server_id"));
        String uri = stringValue(inputs == null ? null : inputs.get("uri"));
        if (serverId.isBlank()) {
            return ToolOutput.failure("server_id is required");
        }
        if (uri.isBlank()) {
            return ToolOutput.failure("uri is required");
        }
        if (resourceReader == null) {
            return ToolOutput.success(List.of());
        }
        try {
            List<?> contents = resourceReader.read(serverId, uri);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object content : contents == null ? List.of() : contents) {
                data.add(ListMcpResourcesTool.resourceMap(content, "uri", "mimeType", "text"));
            }
            return ToolOutput.success(data);
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    @FunctionalInterface
    public interface McpResourceReader {
        List<?> read(String serverId, String uri) throws Exception;
    }
}
