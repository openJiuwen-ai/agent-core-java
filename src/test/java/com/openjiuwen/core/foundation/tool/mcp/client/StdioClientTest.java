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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StdioClient 单元测试
 */
class StdioClientTest {

    private StdioClient stdioClient;
    private McpSyncClient mockMcpClient;

    @BeforeEach
    void setUp() {
        mockMcpClient = mock(McpSyncClient.class);
        stdioClient = new StdioClient("/usr/bin/mcp-server", "test-stdio", Map.of(
                "command", "/usr/bin/mcp-server",
                "args", List.of("--port", "8080")
        ));
        stdioClient.setMcpSyncClientForTest(mockMcpClient);
    }

    @Test
    @DisplayName("构造器 - 基本参数")
    void testConstructorBasic() {
        StdioClient client = new StdioClient("/usr/bin/server", "my-stdio");
        assertEquals("/usr/bin/server", client.getServerPath());
        assertEquals("my-stdio", client.getName());
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("构造器 - 带参数")
    void testConstructorWithParams() {
        Map<String, Object> params = Map.of("command", "node", "args", List.of("server.js"));
        StdioClient client = new StdioClient("/path/to/server", "test", params);
        assertEquals("/path/to/server", client.getServerPath());
    }

    @Test
    @DisplayName("disconnect - 成功")
    void testDisconnectSuccess() throws Exception {
        doNothing().when(mockMcpClient).close();
        var result = stdioClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
        assertFalse(stdioClient.isConnected());
    }

    @Test
    @DisplayName("disconnect - 已断开时直接返回true")
    void testDisconnectAlreadyDisconnected() throws Exception {
        doNothing().when(mockMcpClient).close();
        stdioClient.disconnect(McpClient.NO_TIMEOUT).get();
        // 再次断开
        var result = stdioClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
    }

    @Test
    @DisplayName("disconnect - 异常时仍返回true（清理模式）")
    void testDisconnectWithError() throws Exception {
        doThrow(new RuntimeException("close error")).when(mockMcpClient).close();
        var result = stdioClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result); // Stdio disconnect 即使异常也返回 true (与Python一致)
    }

    @Test
    @DisplayName("listTools - 返回工具列表")
    void testListTools() throws Exception {
        var tool1 = new McpSchema.Tool("stdio-tool1", "Stdio Tool 1", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool1), null)
        );

        var result = stdioClient.listTools(McpClient.NO_TIMEOUT).get();
        assertEquals(1, result.size());
        assertEquals("stdio-tool1", result.get(0).getName());
        assertEquals("test-stdio", result.get(0).getServerName());
    }

    @Test
    @DisplayName("callTool - 提取文本结果")
    void testCallTool() throws Exception {
        var textContent = new McpSchema.TextContent("Stdio Result");
        when(mockMcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(new McpSchema.CallToolResult(List.of(textContent), false));

        var result = stdioClient.callTool("stdio-tool1", Map.of("key", "value"), McpClient.NO_TIMEOUT).get();
        assertEquals("Stdio Result", result);
    }

    @Test
    @DisplayName("getToolInfo - 找到工具")
    void testGetToolInfoFound() throws Exception {
        var tool = new McpSchema.Tool("target", "Target Tool", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool), null)
        );

        var result = stdioClient.getToolInfo("target", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isPresent());
        assertEquals("target", result.get().getName());
    }

    @Test
    @DisplayName("getToolInfo - 未找到工具")
    void testGetToolInfoNotFound() throws Exception {
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(), null)
        );

        var result = stdioClient.getToolInfo("nonexistent", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("未连接时listTools抛异常")
    void testListToolsNotConnected() {
        StdioClient disconnected = new StdioClient("/path", "test");
        var future = disconnected.listTools(McpClient.NO_TIMEOUT);
        assertThrows(Exception.class, future::get);
    }
}

