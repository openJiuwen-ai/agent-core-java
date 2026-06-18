/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists resources exposed by an MCP server.
 *
 * <p>Mirrors Python's {@code ListMcpResourcesTool} in
 * {@code openjiuwen/harness/tools/mcp_tools.py}.</p>
 */
public class ListMcpResourcesTool extends AbstractHarnessTool {

    private final McpResourceLister resourceLister;

    public ListMcpResourcesTool(McpResourceLister resourceLister) {
        super(toolCard("list_mcp_resources", "ListMcpResourcesTool", "List MCP resources for a server."));
        this.resourceLister = resourceLister;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String serverId = stringValue(inputs == null ? null : inputs.get("server_id"));
        if (serverId.isBlank()) {
            return ToolOutput.failure("server_id is required");
        }
        if (resourceLister == null) {
            return ToolOutput.success(List.of());
        }
        try {
            List<?> resources = resourceLister.list(serverId);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object resource : resources == null ? List.of() : resources) {
                data.add(resourceMap(resource, "uri", "name", "mimeType", "description"));
            }
            return ToolOutput.success(data);
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    static Map<String, Object> resourceMap(Object value, String... keys) {
        Map<String, Object> raw = stringObjectMap(value);
        Map<String, Object> result = linkedMap();
        for (String key : keys) {
            result.put(key, raw.get(key));
        }
        if (!result.containsKey("uri") || result.get("uri") == null) {
            result.put("uri", String.valueOf(value));
        }
        if (result.containsKey("name") && result.get("name") == null) {
            result.put("name", "");
        }
        return result;
    }

    @FunctionalInterface
    public interface McpResourceLister {
        List<?> list(String serverId) throws Exception;
    }
}
