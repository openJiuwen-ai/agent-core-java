/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.prompts.tools.HarnessPromptToolsPackage;

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
    private final String language;
    private final String agentId;

    public ListMcpResourcesTool(McpResourceLister resourceLister) {
        this(toolCard("list_mcp_resources", "ListMcpResourcesTool", "List MCP resources for a server."),
                resourceLister, "cn", null);
    }

    public ListMcpResourcesTool(String language, String agentId) {
        this(HarnessPromptToolsPackage.buildToolCard(
                        "list_mcp_resources",
                        "ListMcpResourcesTool",
                        normalizeLanguage(language),
                        agentId),
                serverId -> toList(Runner.resourceMgr().listMcpResources(serverId).toCompletableFuture().join()),
                normalizeLanguage(language),
                agentId);
    }

    private ListMcpResourcesTool(ToolCard card, McpResourceLister resourceLister, String language, String agentId) {
        super(card);
        this.resourceLister = resourceLister;
        this.language = normalizeLanguage(language);
        this.agentId = agentId;
    }

    public String getLanguage() {
        return language;
    }

    public String getAgentId() {
        return agentId;
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

    private static String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? "cn" : language;
    }

    private static List<?> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(item);
            }
            return result;
        }
        return List.of(value);
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
