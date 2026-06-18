/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's MCP tools in {@code openjiuwen/harness/tools/mcp_tools.py}.
 */
class McpToolsTest {

    @Test
    @SuppressWarnings("unchecked")
    void listMcpResourcesRequiresServerAndMapsResources() throws Exception {
        ListMcpResourcesTool missingServer = new ListMcpResourcesTool(server -> List.of());
        ToolOutput failure = (ToolOutput) missingServer.invoke(Map.of(), Map.of());
        assertFalse(failure.isSuccess());
        assertEquals("server_id is required", failure.getError());

        ListMcpResourcesTool tool = new ListMcpResourcesTool(server -> List.of(Map.of(
                "uri", "file://skill",
                "name", "skill",
                "mimeType", "text/markdown",
                "description", "Skill markdown"
        )));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("server_id", "server-a"), Map.of());

        assertTrue(output.isSuccess());
        List<Map<String, Object>> resources = (List<Map<String, Object>>) output.getData();
        assertEquals("file://skill", resources.get(0).get("uri"));
        assertEquals("skill", resources.get(0).get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readMcpResourceRequiresUriAndMapsContents() throws Exception {
        ReadMcpResourceTool missingUri = new ReadMcpResourceTool((server, uri) -> List.of());
        ToolOutput failure = (ToolOutput) missingUri.invoke(Map.of("server_id", "server-a"), Map.of());
        assertFalse(failure.isSuccess());
        assertEquals("uri is required", failure.getError());

        ReadMcpResourceTool tool = new ReadMcpResourceTool((server, uri) -> List.of(Map.of(
                "uri", uri,
                "mimeType", "text/plain",
                "text", "hello"
        )));

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("server_id", "server-a", "uri", "file://hello"),
                Map.of()
        );

        assertTrue(output.isSuccess());
        List<Map<String, Object>> contents = (List<Map<String, Object>>) output.getData();
        assertEquals("hello", contents.get(0).get("text"));
    }
}
