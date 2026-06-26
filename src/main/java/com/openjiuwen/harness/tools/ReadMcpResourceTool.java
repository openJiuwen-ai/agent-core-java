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
 * Reads a single MCP resource by URI.
 *
 * <p>Mirrors Python's {@code ReadMcpResourceTool} in
 * {@code openjiuwen/harness/tools/mcp_tools.py}.</p>
 */
public class ReadMcpResourceTool extends AbstractHarnessTool {

    private final McpResourceReader resourceReader;
    private final String language;
    private final String agentId;

    public ReadMcpResourceTool(McpResourceReader resourceReader) {
        this(toolCard("read_mcp_resource", "ReadMcpResourceTool", "Read an MCP resource by URI."),
                resourceReader, "cn", null);
    }

    public ReadMcpResourceTool(String language, String agentId) {
        this(HarnessPromptToolsPackage.buildToolCard(
                        "read_mcp_resource",
                        "ReadMcpResourceTool",
                        normalizeLanguage(language),
                        agentId),
                (serverId, uri) -> toList(Runner.resourceMgr().readMcpResource(serverId, uri).toCompletableFuture().join()),
                normalizeLanguage(language),
                agentId);
    }

    private ReadMcpResourceTool(ToolCard card, McpResourceReader resourceReader, String language, String agentId) {
        super(card);
        this.resourceReader = resourceReader;
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

    @FunctionalInterface
    public interface McpResourceReader {
        List<?> read(String serverId, String uri) throws Exception;
    }
}
