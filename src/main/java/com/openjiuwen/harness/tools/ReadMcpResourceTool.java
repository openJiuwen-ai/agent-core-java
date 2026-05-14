package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ReadMcpResourceTool} in {@code openjiuwen.harness.tools.mcp_tools}.
 */
public class ReadMcpResourceTool extends AbstractHarnessTool {

    @FunctionalInterface
    public interface McpResourceReader {
        List<Map<String, Object>> read(String serverId, String uri, Map<String, Object> options) throws Exception;
    }

    private final McpResourceReader resourceReader;

    public ReadMcpResourceTool() {
        this((serverId, uri, options) -> List.of());
    }

    public ReadMcpResourceTool(McpResourceReader resourceReader) {
        super(toolCard("read_mcp_resource", "read_mcp_resource", "Read an MCP resource by URI."), null);
        this.resourceReader = resourceReader;
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
            return new ToolOutput(true, resourceReader.read(serverId, uri, inputs), null);
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }
}
