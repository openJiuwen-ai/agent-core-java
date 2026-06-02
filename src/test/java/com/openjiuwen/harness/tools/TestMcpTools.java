/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_mcp_tools.py} in
 * {@code tests/unit_tests/harness/tools/test_mcp_tools.py}.
 */
@DisplayName("MCP tools tests")
class TestMcpTools {

    @Nested
    class ListMcpResourcesToolInvoke {

        @Test
        void testReturnsMappedResourceList() {
            ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of(
                    new Resource("res://a", "Alpha", "text/plain", "first"),
                    new Resource("res://b", "Beta", null, null)));

            ToolOutput result = invokeList(tool, Map.of("server_id", "my-server"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(
                    Map.of("uri", "res://a", "name", "Alpha", "mimeType", "text/plain", "description", "first"),
                    mapWithNulls("res://b", "Beta", null, null)
            ), result.getData());
        }

        @Test
        void testEmptyResourceListReturnsEmptyData() {
            ToolOutput result = invokeList(new ListMcpResourcesTool((serverId, options) -> List.of()),
                    Map.of("server_id", "my-server"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(), result.getData());
        }

        @Test
        void testNoneResourcesReturnsEmptyData() {
            ToolOutput result = invokeList(new ListMcpResourcesTool((serverId, options) -> null),
                    Map.of("server_id", "my-server"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(), result.getData());
        }

        @Test
        void testMissingServerIdReturnsError() {
            ToolOutput result = invokeList(new ListMcpResourcesTool(), Map.of());

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("server_id"));
        }

        @Test
        void testEmptyServerIdReturnsError() {
            ToolOutput result = invokeList(new ListMcpResourcesTool(), Map.of("server_id", ""));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("server_id"));
        }

        @Test
        void testResourceMgrExceptionReturnsError() {
            ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> {
                throw new IllegalStateException("connection refused");
            });

            ToolOutput result = invokeList(tool, Map.of("server_id", "bad-server"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("connection refused"));
        }

        @Test
        void testPassesServerIdToResourceMgr() {
            AtomicReference<String> seenServerId = new AtomicReference<>();
            ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> {
                seenServerId.set(serverId);
                return List.of();
            });

            invokeList(tool, Map.of("server_id", "target-server"));

            assertEquals("target-server", seenServerId.get());
        }

        @Test
        void testResourceWithoutAttributesFallsBackToStr() {
            Object plain = new Object();
            ListMcpResourcesTool tool = new ListMcpResourcesTool((serverId, options) -> List.of(plain));

            ToolOutput result = invokeList(tool, Map.of("server_id", "s"));

            assertTrue(result.isSuccess(), result.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = ((List<Map<String, Object>>) result.getData()).get(0);
            assertEquals(String.valueOf(plain), resource.get("uri"));
            assertEquals("", resource.get("name"));
            assertEquals(null, resource.get("mimeType"));
            assertEquals(null, resource.get("description"));
        }
    }

    @Nested
    class ReadMcpResourceToolInvoke {

        @Test
        void testReturnsMappedContentList() {
            ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> List.of(
                    new Content("res://a", "text/plain", "hello"),
                    new Content("res://b", null, null)));

            ToolOutput result = invokeRead(tool, Map.of("server_id", "my-server", "uri", "res://a"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(
                    Map.of("uri", "res://a", "mimeType", "text/plain", "text", "hello"),
                    contentWithNulls("res://b", null, null)
            ), result.getData());
        }

        @Test
        void testEmptyContentsReturnsEmptyData() {
            ToolOutput result = invokeRead(new ReadMcpResourceTool((serverId, uri, options) -> List.of()),
                    Map.of("server_id", "s", "uri", "res://x"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(), result.getData());
        }

        @Test
        void testNoneContentsReturnsEmptyData() {
            ToolOutput result = invokeRead(new ReadMcpResourceTool((serverId, uri, options) -> null),
                    Map.of("server_id", "s", "uri", "res://x"));

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(List.of(), result.getData());
        }

        @Test
        void testMissingServerIdReturnsError() {
            ToolOutput result = invokeRead(new ReadMcpResourceTool(), Map.of("uri", "res://x"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("server_id"));
        }

        @Test
        void testMissingUriReturnsError() {
            ToolOutput result = invokeRead(new ReadMcpResourceTool(), Map.of("server_id", "my-server"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("uri"));
        }

        @Test
        void testEmptyUriReturnsError() {
            ToolOutput result = invokeRead(new ReadMcpResourceTool(), Map.of("server_id", "my-server", "uri", ""));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("uri"));
        }

        @Test
        void testResourceMgrExceptionReturnsError() {
            ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> {
                throw new IllegalStateException("server not found");
            });

            ToolOutput result = invokeRead(tool, Map.of("server_id", "bad-server", "uri", "res://x"));

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("server not found"));
        }

        @Test
        void testPassesServerIdAndUriToResourceMgr() {
            AtomicReference<String> seenServerId = new AtomicReference<>();
            AtomicReference<String> seenUri = new AtomicReference<>();
            ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> {
                seenServerId.set(serverId);
                seenUri.set(uri);
                return List.of();
            });

            invokeRead(tool, Map.of("server_id", "target-server", "uri", "res://doc"));

            assertEquals("target-server", seenServerId.get());
            assertEquals("res://doc", seenUri.get());
        }

        @Test
        void testContentWithoutAttributesFallsBackToStr() {
            Object plain = new Object();
            ReadMcpResourceTool tool = new ReadMcpResourceTool((serverId, uri, options) -> List.of(plain));

            ToolOutput result = invokeRead(tool, Map.of("server_id", "s", "uri", "res://x"));

            assertTrue(result.isSuccess(), result.getError());
            @SuppressWarnings("unchecked")
            Map<String, Object> content = ((List<Map<String, Object>>) result.getData()).get(0);
            assertEquals(String.valueOf(plain), content.get("uri"));
            assertEquals(null, content.get("mimeType"));
            assertEquals(null, content.get("text"));
        }
    }

    private ToolOutput invokeList(ListMcpResourcesTool tool, Map<String, Object> inputs) {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    private ToolOutput invokeRead(ReadMcpResourceTool tool, Map<String, Object> inputs) {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    private Map<String, Object> mapWithNulls(String uri, String name, Object mimeType, Object description) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("uri", uri);
        map.put("name", name);
        map.put("mimeType", mimeType);
        map.put("description", description);
        return map;
    }

    private Map<String, Object> contentWithNulls(String uri, Object mimeType, Object text) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("uri", uri);
        map.put("mimeType", mimeType);
        map.put("text", text);
        return map;
    }

    private record Resource(String uri, String name, String mimeType, String description) {
    }

    private record Content(String uri, String mimeType, String text) {
    }
}
