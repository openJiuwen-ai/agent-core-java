package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Mirrors Python's {@code ListMcpResourcesTool} in {@code openjiuwen.harness.tools.mcp_tools}.
 */
public class ListMcpResourcesTool extends AbstractHarnessTool {

    private final BiFunction<String, Map<String, Object>, List<Map<String, Object>>> resourceProvider;

    public ListMcpResourcesTool() {
        this((serverId, options) -> List.of());
    }

    public ListMcpResourcesTool(BiFunction<String, Map<String, Object>, List<Map<String, Object>>> resourceProvider) {
        super(toolCard("list_mcp_resources", "list_mcp_resources", "List MCP resources for a server."), null);
        this.resourceProvider = resourceProvider;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String serverId = inputs.get("server_id") == null ? "" : String.valueOf(inputs.get("server_id"));
        if (serverId.isBlank()) {
            return new ToolOutput(false, null, "server_id is required");
        }
        try {
            List<Map<String, Object>> data = resourceProvider.apply(serverId, inputs);
            return new ToolOutput(true, Objects.requireNonNullElseGet(data, List::of), null);
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }
}
