package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mirrors Python's {@code ReadMcpResourceTool} in {@code openjiuwen.harness.tools.mcp_tools}.
 */
public class ReadMcpResourceTool extends AbstractHarnessTool {

    @FunctionalInterface
    public interface McpResourceReader {
        List<?> read(String serverId, String uri, Map<String, Object> options) throws Exception;
    }

    private final McpResourceReader resourceReader;
    private final String language;
    private final String agentId;

    public ReadMcpResourceTool() {
        this(null, null);
    }

    public ReadMcpResourceTool(String language, String agentId) {
        this((serverId, uri, options) -> List.of(), language, agentId);
    }

    public ReadMcpResourceTool(McpResourceReader resourceReader) {
        this(resourceReader, null, null);
    }

    public ReadMcpResourceTool(McpResourceReader resourceReader, String language, String agentId) {
        super(toolCard("read_mcp_resource", "read_mcp_resource", "Read an MCP resource by URI."), null);
        this.resourceReader = resourceReader;
        this.language = language;
        this.agentId = agentId;
    }

    public String getLanguage() {
        return language;
    }

    public String getAgentId() {
        return agentId;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String serverId = inputs.get("server_id") == null ? "" : String.valueOf(inputs.get("server_id"));
        String uri = inputs.get("uri") == null ? "" : String.valueOf(inputs.get("uri"));
        if (serverId.isBlank()) {
            return new ToolOutput(false, null, "server_id is required");
        }
        if (uri.isBlank()) {
            return new ToolOutput(false, null, "uri is required");
        }
        try {
            List<?> contents = resourceReader.read(serverId, uri, inputs);
            List<Map<String, Object>> data = Objects.requireNonNullElseGet(contents, List::of).stream()
                    .map(ReadMcpResourceTool::mapContent)
                    .collect(Collectors.toList());
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }

    private static Map<String, Object> mapContent(Object content) {
        if (content instanceof Map<?, ?> raw) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            Object uri = raw.get("uri");
            mapped.put("uri", uri != null ? uri : String.valueOf(content));
            mapped.put("mimeType", raw.get("mimeType"));
            mapped.put("text", raw.get("text"));
            return mapped;
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        Object uri = readField(content, "uri");
        mapped.put("uri", uri != null ? uri : String.valueOf(content));
        mapped.put("mimeType", readField(content, "mimeType"));
        mapped.put("text", readField(content, "text"));
        return mapped;
    }

}
