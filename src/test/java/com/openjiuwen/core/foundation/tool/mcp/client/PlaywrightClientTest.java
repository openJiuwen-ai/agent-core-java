// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PlaywrightClient 单元测试
 */
class PlaywrightClientTest {

    private PlaywrightClient playwrightClient;
    private McpSyncClient mockMcpClient;

    @BeforeEach
    void setUp() {
        mockMcpClient = mock(McpSyncClient.class);
        playwrightClient = new PlaywrightClient("http://localhost:3000/sse", "playwright-server");
        playwrightClient.setMcpSyncClientForTest(mockMcpClient);
    }

    @Test
    @DisplayName("构造器 - HTTP URL")
    void testConstructorWithUrl() {
        PlaywrightClient client = new PlaywrightClient("http://localhost:3000/sse", "pw");
        assertEquals("http://localhost:3000/sse", client.getServerPath());
        assertEquals("pw", client.getName());
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("构造器 - StdioServerParameters")
    void testConstructorWithStdioParams() {
        StdioServerParameters params = StdioServerParameters.builder()
                .command("npx")
                .addArg("playwright-mcp")
                .build();
        PlaywrightClient client = new PlaywrightClient(params, "pw-stdio");
        assertEquals(params, client.getServerPath());
        assertEquals("pw-stdio", client.getName());
    }

    @Test
    @DisplayName("disconnect - 成功")
    void testDisconnectSuccess() throws Exception {
        doNothing().when(mockMcpClient).close();
        var result = playwrightClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
        assertFalse(playwrightClient.isConnected());
    }

    @Test
    @DisplayName("listTools - 返回浏览器工具列表")
    void testListTools() throws Exception {
        var tool1 = new McpSchema.Tool("navigate", "Navigate to URL", "{}");
        var tool2 = new McpSchema.Tool("click", "Click element", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool1, tool2), null)
        );

        var result = playwrightClient.listTools(McpClient.NO_TIMEOUT).get();
        assertEquals(2, result.size());
        assertEquals("navigate", result.get(0).getName());
        assertEquals("playwright-server", result.get(0).getServerName());
    }

    @Test
    @DisplayName("callTool - 提取文本结果")
    void testCallTool() throws Exception {
        var textContent = new McpSchema.TextContent("Page loaded");
        when(mockMcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(new McpSchema.CallToolResult(List.of(textContent), false));

        var result = playwrightClient.callTool("navigate", Map.of("url", "http://example.com"), McpClient.NO_TIMEOUT).get();
        assertEquals("Page loaded", result);
    }

    @Test
    @DisplayName("getToolInfo - 找到工具")
    void testGetToolInfoFound() throws Exception {
        var tool = new McpSchema.Tool("click", "Click element", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool), null)
        );

        var result = playwrightClient.getToolInfo("click", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isPresent());
        assertEquals("click", result.get().getName());
    }

    @Test
    @DisplayName("getToolInfo - 未找到工具")
    void testGetToolInfoNotFound() throws Exception {
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(), null)
        );

        var result = playwrightClient.getToolInfo("nonexistent", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("未连接时callTool抛异常")
    void testCallToolNotConnected() {
        PlaywrightClient disconnected = new PlaywrightClient("http://localhost/sse", "test");
        var future = disconnected.callTool("navigate", Map.of(), McpClient.NO_TIMEOUT);
        assertThrows(Exception.class, future::get);
    }

    @Test
    @DisplayName("disconnect - 异常时仍返回true")
    void testDisconnectWithError() throws Exception {
        doThrow(new RuntimeException("cleanup error")).when(mockMcpClient).close();
        var result = playwrightClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
    }
}

