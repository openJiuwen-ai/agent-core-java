package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's MCP tool invoke tests for P2-01.
 */
class McpToolsTest {

    private Map<String, Object> row(String uri, String name, String mimeType, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uri", uri);
        row.put("name", name);
        row.put("mimeType", mimeType);
        row.put("description", description);
        return row;
    }

    @Test
    void listMcpResourcesReturnsMappedResourceList() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of(
                row("res://a", "Alpha", "text/plain", "first"),
                row("res://b", "Beta", null, null)
        ));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals(2, data.size());
        assertEquals("res://a", data.get(0).get("uri"));
        assertEquals("Alpha", data.get(0).get("name"));
    }

    @Test
    void listMcpResourcesRequiresServerId() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool();
        ToolOutput output = (ToolOutput) tool.invoke(Map.of(), Map.of());
        assertFalse(output.isSuccess());
        assertEquals("server_id is required", output.getError());
    }

    @Test
    void listMcpResourcesAllowsEmptyData() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of());

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertTrue(data.isEmpty());
    }

    @Test
    void listMcpResourcesNormalizesNullDataToEmptyList() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> null);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertTrue(data.isEmpty());
    }

    @Test
    void listMcpResourcesSurfacesProviderExceptions() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> {
            throw new IllegalStateException("connection refused");
        });

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "bad-server"), Map.of());

        assertFalse(output.isSuccess());
        assertEquals("connection refused", output.getError());
    }

    @Test
    void listMcpResourcesSupportsFallbackStringRows() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of(
                row("java.lang.Object@1", "", null, null)
        ));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "s"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals("java.lang.Object@1", data.get(0).get("uri"));
        assertEquals("", data.get(0).get("name"));
        assertEquals(null, data.get(0).get("mimeType"));
        assertEquals(null, data.get(0).get("description"));
    }

    @Test
    void readMcpResourceReturnsContents() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> List.of(
                Map.of("uri", uri, "mimeType", "text/markdown", "text", "# hello")
        ));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server", "uri", "res://doc"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals(1, data.size());
        assertEquals("# hello", data.get(0).get("text"));
    }

    @Test
    void readMcpResourceRequiresUri() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool();
        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server"), Map.of());
        assertFalse(output.isSuccess());
        assertEquals("uri is required", output.getError());
    }

    @Test
    void readMcpResourceNormalizesNullDataToEmptyList() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> null);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server", "uri", "res://doc"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertTrue(data.isEmpty());
    }
}
