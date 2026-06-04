package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Mirrors Python's {@code ListMcpResourcesTool} in {@code openjiuwen.harness.tools.mcp_tools}.
 */
public class ListMcpResourcesTool extends AbstractHarnessTool {

    private final BiFunction<String, Map<String, Object>, List<?>> resourceProvider;
    private final String language;
    private final String agentId;

    public ListMcpResourcesTool() {
        this(null, null);
    }

    public ListMcpResourcesTool(String language, String agentId) {
        this((serverId, options) -> List.of(), language, agentId);
    }

    public ListMcpResourcesTool(BiFunction<String, Map<String, Object>, List<?>> resourceProvider) {
        this(resourceProvider, null, null);
    }

    public ListMcpResourcesTool(
            BiFunction<String, Map<String, Object>, List<?>> resourceProvider,
            String language,
            String agentId
    ) {
        super(toolCard("list_mcp_resources", "list_mcp_resources", "List MCP resources for a server."), null);
        this.resourceProvider = resourceProvider;
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
        if (serverId.isBlank()) {
            return new ToolOutput(false, null, "server_id is required");
        }
        try {
            List<?> resources = resourceProvider.apply(serverId, inputs);
            List<Map<String, Object>> data = Objects.requireNonNullElseGet(resources, List::of).stream()
                    .map(ListMcpResourcesTool::mapResource)
                    .collect(Collectors.toList());
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }

    private static Map<String, Object> mapResource(Object resource) {
        if (resource instanceof Map<?, ?> raw) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            Object uri = raw.get("uri");
            Object name = raw.get("name");
            mapped.put("uri", uri != null ? uri : String.valueOf(resource));
            mapped.put("name", name != null ? name : "");
            mapped.put("mimeType", raw.get("mimeType"));
            mapped.put("description", raw.get("description"));
            return mapped;
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        Object uri = readField(resource, "uri");
        mapped.put("uri", uri != null ? uri : String.valueOf(resource));
        mapped.put("name", Objects.requireNonNullElse(readField(resource, "name"), ""));
        mapped.put("mimeType", readField(resource, "mimeType"));
        mapped.put("description", readField(resource, "description"));
        return mapped;
    }

}
