/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_mcp_tools} in
 * {@code tests/unit_tests/harness/tools/test_mcp_tools.py}.
 */
class McpToolsTest {

    @Test
    @SuppressWarnings("unchecked")
    void listMcpResourcesReturnsMappedResourceList() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of(
                linkedMap("uri", "res://a", "name", "Alpha", "mimeType", "text/plain", "description", "first"),
                linkedMap("uri", "res://b", "name", "Beta")
        ));

        ToolOutput output = invokeList(tool, linkedMap("server_id", "my-server"));

        assertTrue(output.isSuccess());
        List<Map<String, Object>> resources = (List<Map<String, Object>>) output.getData();
        assertEquals(2, resources.size());
        assertEquals(linkedMap("uri", "res://a", "name", "Alpha", "mimeType", "text/plain", "description", "first"),
                resources.get(0));
        assertEquals(linkedMap("uri", "res://b", "name", "Beta", "mimeType", null, "description", null),
                resources.get(1));
    }

    @Test
    void listMcpResourcesEmptyResourceListReturnsEmptyData() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of());

        ToolOutput output = invokeList(tool, linkedMap("server_id", "my-server"));

        assertTrue(output.isSuccess());
        assertEquals(List.of(), output.getData());
    }

    @Test
    void listMcpResourcesNullResourcesReturnsEmptyData() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> null);

        ToolOutput output = invokeList(tool, linkedMap("server_id", "my-server"));

        assertTrue(output.isSuccess());
        assertEquals(List.of(), output.getData());
    }

    @Test
    void listMcpResourcesMissingServerIdReturnsError() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of());

        ToolOutput output = invokeList(tool, Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("server_id"));
    }

    @Test
    void listMcpResourcesEmptyServerIdReturnsError() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of());

        ToolOutput output = invokeList(tool, linkedMap("server_id", ""));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("server_id"));
    }

    @Test
    void listMcpResourcesResourceManagerExceptionReturnsError() throws Exception {
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> {
            throw new RuntimeException("connection refused");
        });

        ToolOutput output = invokeList(tool, linkedMap("server_id", "bad-server"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("connection refused"));
    }

    @Test
    void listMcpResourcesPassesServerIdToResourceManager() throws Exception {
        RecordingLister lister = new RecordingLister(List.of());
        ListMcpResourcesTool tool = new ListMcpResourcesTool(lister);

        invokeList(tool, linkedMap("server_id", "target-server"));

        assertEquals(List.of("target-server"), lister.serverIds);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMcpResourcesResourceWithoutAttributesFallsBackToString() throws Exception {
        Object plain = new Object();
        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of(plain));

        ToolOutput output = invokeList(tool, linkedMap("server_id", "s"));

        assertTrue(output.isSuccess());
        Map<String, Object> resource = ((List<Map<String, Object>>) output.getData()).get(0);
        assertEquals(String.valueOf(plain), resource.get("uri"));
        assertEquals("", resource.get("name"));
        assertEquals(null, resource.get("mimeType"));
        assertEquals(null, resource.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readMcpResourceReturnsMappedContentList() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of(
                linkedMap("uri", "res://a", "mimeType", "text/plain", "text", "hello"),
                linkedMap("uri", "res://b")
        ));

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "my-server", "uri", "res://a"));

        assertTrue(output.isSuccess());
        List<Map<String, Object>> contents = (List<Map<String, Object>>) output.getData();
        assertEquals(2, contents.size());
        assertEquals(linkedMap("uri", "res://a", "mimeType", "text/plain", "text", "hello"), contents.get(0));
        assertEquals(linkedMap("uri", "res://b", "mimeType", null, "text", null), contents.get(1));
    }

    @Test
    void readMcpResourceEmptyContentsReturnsEmptyData() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of());

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "s", "uri", "res://x"));

        assertTrue(output.isSuccess());
        assertEquals(List.of(), output.getData());
    }

    @Test
    void readMcpResourceNullContentsReturnsEmptyData() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> null);

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "s", "uri", "res://x"));

        assertTrue(output.isSuccess());
        assertEquals(List.of(), output.getData());
    }

    @Test
    void readMcpResourceMissingServerIdReturnsError() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of());

        ToolOutput output = invokeRead(tool, linkedMap("uri", "res://x"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("server_id"));
    }

    @Test
    void readMcpResourceMissingUriReturnsError() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of());

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "my-server"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("uri"));
    }

    @Test
    void readMcpResourceEmptyUriReturnsError() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of());

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "my-server", "uri", ""));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("uri"));
    }

    @Test
    void readMcpResourceResourceManagerExceptionReturnsError() throws Exception {
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> {
            throw new RuntimeException("server not found");
        });

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "bad-server", "uri", "res://x"));

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("server not found"));
    }

    @Test
    void readMcpResourcePassesServerIdAndUriToResourceManager() throws Exception {
        RecordingReader reader = new RecordingReader(List.of());
        ReadMcpResourceTool tool = new ReadMcpResourceTool(reader);

        invokeRead(tool, linkedMap("server_id", "target-server", "uri", "res://doc"));

        assertEquals(List.of("target-server"), reader.serverIds);
        assertEquals(List.of("res://doc"), reader.uris);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readMcpResourceContentWithoutAttributesFallsBackToString() throws Exception {
        Object plain = new Object();
        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of(plain));

        ToolOutput output = invokeRead(tool, linkedMap("server_id", "s", "uri", "res://x"));

        assertTrue(output.isSuccess());
        Map<String, Object> content = ((List<Map<String, Object>>) output.getData()).get(0);
        assertEquals(String.valueOf(plain), content.get("uri"));
        assertEquals(null, content.get("mimeType"));
        assertEquals(null, content.get("text"));
    }

    private static ToolOutput invokeList(ListMcpResourcesTool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    private static ToolOutput invokeRead(ReadMcpResourceTool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class RecordingLister implements ListMcpResourcesTool.McpResourceLister {
        private final List<?> resources;
        private final List<String> serverIds = new ArrayList<>();

        private RecordingLister(List<?> resources) {
            this.resources = resources;
        }

        @Override
        public List<?> list(String serverId) {
            serverIds.add(serverId);
            return resources;
        }
    }

    private static final class RecordingReader implements ReadMcpResourceTool.McpResourceReader {
        private final List<?> contents;
        private final List<String> serverIds = new ArrayList<>();
        private final List<String> uris = new ArrayList<>();

        private RecordingReader(List<?> contents) {
            this.contents = contents;
        }

        @Override
        public List<?> read(String serverId, String uri) {
            serverIds.add(serverId);
            uris.add(uri);
            return contents;
        }
    }
}
