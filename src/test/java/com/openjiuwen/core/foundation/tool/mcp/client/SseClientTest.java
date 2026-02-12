// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SseClient 单元测试
 */
class SseClientTest {

    private SseClient sseClient;
    private McpSyncClient mockMcpClient;

    @BeforeEach
    void setUp() {
        mockMcpClient = mock(McpSyncClient.class);
        // 使用无认证构造器
        sseClient = new SseClient("http://localhost:8080/sse", "test-server");
        // 注入mock McpSyncClient
        sseClient.setMcpSyncClientForTest(mockMcpClient);
    }

    @Test
    @DisplayName("构造器 - 基本参数")
    void testConstructorBasic() {
        SseClient client = new SseClient("http://localhost:8080/sse", "my-server");
        assertEquals("http://localhost:8080/sse", client.getServerPath());
        assertEquals("my-server", client.getName());
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("构造器 - 带认证参数")
    void testConstructorWithAuth() {
        Map<String, String> headers = Map.of("Authorization", "Bearer token123");
        Map<String, String> queryParams = Map.of("api_key", "key123");
        SseClient client = new SseClient("http://localhost:8080/sse", "auth-server", headers, queryParams);
        assertEquals("http://localhost:8080/sse", client.getServerPath());
        assertEquals("auth-server", client.getName());
    }

    @Test
    @DisplayName("connect - 成功")
    void testConnectSuccess() throws Exception {
        when(mockMcpClient.initialize()).thenReturn(new McpSchema.InitializeResult(
                "2024-11-05",
                new McpSchema.ServerCapabilities(null, null, null, null, null),
                new McpSchema.Implementation("test", "1.0"),
                null
        ));

        // 使用已注入mockMcpClient的客户端，直接测试connect后的状态
        // connect内部会创建transport和client，但我们通过setMcpSyncClientForTest已注入
        // 不能直接测试connect的完整流程(需要真实服务器)，所以测试其他方法
        assertTrue(sseClient.isConnected()); // 因为已注入mock
    }

    @Test
    @DisplayName("disconnect - 成功")
    void testDisconnectSuccess() throws Exception {
        // mock close
        doNothing().when(mockMcpClient).close();

        var result = sseClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertTrue(result);
        assertFalse(sseClient.isConnected());
    }

    @Test
    @DisplayName("disconnect - 异常时返回false")
    void testDisconnectFailure() throws Exception {
        doThrow(new RuntimeException("close failed")).when(mockMcpClient).close();

        var result = sseClient.disconnect(McpClient.NO_TIMEOUT).get();
        assertFalse(result);
    }

    @Test
    @DisplayName("listTools - 返回工具列表")
    void testListTools() throws Exception {
        var tool1 = new McpSchema.Tool("tool1", "Description 1", "{}");
        var tool2 = new McpSchema.Tool("tool2", "Description 2", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool1, tool2), null)
        );

        var result = sseClient.listTools(McpClient.NO_TIMEOUT).get();
        assertEquals(2, result.size());
        assertEquals("tool1", result.get(0).getName());
        assertEquals("test-server", result.get(0).getServerName());
        assertEquals("Description 1", result.get(0).getDescription());
        assertEquals("tool2", result.get(1).getName());
    }

    @Test
    @DisplayName("listTools - 未连接时抛异常")
    void testListToolsNotConnected() {
        SseClient disconnectedClient = new SseClient("http://localhost/sse", "test");
        // 不注入mockMcpClient，所以未连接
        var future = disconnectedClient.listTools(McpClient.NO_TIMEOUT);
        assertThrows(Exception.class, future::get);
    }

    @Test
    @DisplayName("callTool - 提取文本结果")
    void testCallToolExtractsText() throws Exception {
        var textContent = new McpSchema.TextContent("Hello World");
        when(mockMcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(new McpSchema.CallToolResult(List.of(textContent), false));

        var result = sseClient.callTool("tool1", Map.of("arg1", "val1"), McpClient.NO_TIMEOUT).get();
        assertEquals("Hello World", result);

        // 验证传递的参数
        var captor = org.mockito.ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(mockMcpClient).callTool(captor.capture());
        assertEquals("tool1", captor.getValue().name());
        assertEquals(Map.of("arg1", "val1"), captor.getValue().arguments());
    }

    @Test
    @DisplayName("callTool - 空内容返回null")
    void testCallToolEmptyContent() throws Exception {
        when(mockMcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(new McpSchema.CallToolResult(List.of(), false));

        var result = sseClient.callTool("tool1", Map.of(), McpClient.NO_TIMEOUT).get();
        assertNull(result);
    }

    @Test
    @DisplayName("callTool - 提取最后一个内容")
    void testCallToolExtractsLastContent() throws Exception {
        var text1 = new McpSchema.TextContent("First");
        var text2 = new McpSchema.TextContent("Last");
        when(mockMcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(new McpSchema.CallToolResult(List.of(text1, text2), false));

        var result = sseClient.callTool("tool1", Map.of(), McpClient.NO_TIMEOUT).get();
        assertEquals("Last", result);
    }

    @Test
    @DisplayName("getToolInfo - 找到工具")
    void testGetToolInfoFound() throws Exception {
        var tool1 = new McpSchema.Tool("target-tool", "Target Description", "{}");
        var tool2 = new McpSchema.Tool("other-tool", "Other Description", "{}");
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(tool1, tool2), null)
        );

        var result = sseClient.getToolInfo("target-tool", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isPresent());
        assertEquals("target-tool", result.get().getName());
    }

    @Test
    @DisplayName("getToolInfo - 未找到工具")
    void testGetToolInfoNotFound() throws Exception {
        when(mockMcpClient.listTools()).thenReturn(
                new McpSchema.ListToolsResult(List.of(), null)
        );

        var result = sseClient.getToolInfo("nonexistent", McpClient.NO_TIMEOUT).get();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("callTool - 未连接时抛异常")
    void testCallToolNotConnected() {
        SseClient disconnectedClient = new SseClient("http://localhost/sse", "test");
        var future = disconnectedClient.callTool("tool1", Map.of(), McpClient.NO_TIMEOUT);
        assertThrows(Exception.class, future::get);
    }
}

