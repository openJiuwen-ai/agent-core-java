package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
    void listMcpResourcesRequiresNonEmptyServerId() {
        ListMcpResourcesTool tool = new ListMcpResourcesTool();

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", ""), Map.of());

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
    void listMcpResourcesPassesServerIdToProvider() {
        AtomicReference<String> seenServerId = new AtomicReference<>();
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> {
            seenServerId.set(serverId);
            return List.of();
        });

        tool.invoke(Map.of("server_id", "target-server"), Map.of());

        assertEquals("target-server", seenServerId.get());
    }

    @Test
    void listMcpResourcesSupportsFallbackStringRows() {
        PlainValue plain = new PlainValue("plain-resource");
        ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of(plain));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "s"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals("plain-resource", data.get(0).get("uri"));
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
    void readMcpResourceAllowsEmptyData() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> List.of());

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "s", "uri", "res://x"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertTrue(data.isEmpty());
    }

    @Test
    void readMcpResourceRequiresServerId() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool();

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("uri", "res://x"), Map.of());

        assertFalse(output.isSuccess());
        assertEquals("server_id is required", output.getError());
    }

    @Test
    void readMcpResourceRequiresUri() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool();
        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server"), Map.of());
        assertFalse(output.isSuccess());
        assertEquals("uri is required", output.getError());
    }

    @Test
    void readMcpResourceRequiresNonEmptyUri() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool();

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "my-server", "uri", ""), Map.of());

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

    @Test
    void readMcpResourceSurfacesProviderExceptions() {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> {
            throw new IllegalStateException("server not found");
        });

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "bad-server", "uri", "res://x"), Map.of());

        assertFalse(output.isSuccess());
        assertEquals("server not found", output.getError());
    }

    @Test
    void readMcpResourcePassesServerIdAndUriToProvider() {
        AtomicReference<String> seenServerId = new AtomicReference<>();
        AtomicReference<String> seenUri = new AtomicReference<>();
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> {
            seenServerId.set(serverId);
            seenUri.set(uri);
            return List.of();
        });

        tool.invoke(Map.of("server_id", "target-server", "uri", "res://doc"), Map.of());

        assertEquals("target-server", seenServerId.get());
        assertEquals("res://doc", seenUri.get());
    }

    @Test
    void readMcpResourceSupportsFallbackStringRows() {
        PlainValue plain = new PlainValue("plain-content");
        ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> List.of(plain));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "s", "uri", "res://x"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals("plain-content", data.get(0).get("uri"));
        assertEquals(null, data.get(0).get("mimeType"));
        assertEquals(null, data.get(0).get("text"));
    }

    private record PlainValue(String value) {
        @Override
        public String toString() {
            return value;
        }
    }
}
