// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.tracer.TracerDecorator;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolMgr 测试类
 * 
 * 对应Python: test_tool_manager.py
 */
@DisplayName("ToolMgr 测试")
class ToolMgrTest {
    
    static class MockTool {
        private final String toolId;
        private final String name;
        
        MockTool(String toolId) {
            this(toolId, toolId);
        }
        
        MockTool(String toolId, String name) {
            this.toolId = toolId;
            this.name = name;
        }
        
        String getToolId() {
            return toolId;
        }
        
        String getName() {
            return name;
        }
    }
    
    static class MockMcpServerConfig {
        private final String serverId;
        private final String serverName;
        private final String clientType;
        private final String serverPath;
        
        MockMcpServerConfig(String serverId, String serverName, String clientType) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.clientType = clientType;
            this.serverPath = "http://localhost:8080";
        }
        
        String getServerId() {
            return serverId;
        }
        
        String getServerName() {
            return serverName;
        }
        
        String getClientType() {
            return clientType;
        }
        
        String getServerPath() {
            return serverPath;
        }
    }
    
    @Nested
    @DisplayName("基本CRUD操作")
    class BasicCRUDTest {
        
        @Test
        @DisplayName("成功添加Tool")
        void testAddToolSuccess() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool = new MockTool("tool_1");
            
            mgr.addTool("tool_1", tool);
            
            assertTrue(mgr.hasTool("tool_1"));
        }
        
        @Test
        @DisplayName("添加重复ID抛出异常")
        void testAddToolDuplicateIdRaisesError() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool1 = new MockTool("tool_1");
            MockTool tool2 = new MockTool("tool_1");
            
            mgr.addTool("tool_1", tool1);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addTool("tool_1", tool2)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already exist"));
        }
        
        @Test
        @DisplayName("获取Tool（无Session时包装在TracedTool中）")
        void testGetToolWithoutSession() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool = new MockTool("tool_1");
            
            mgr.addTool("tool_1", tool);
            
            TracerDecorator.TracedTool<MockTool> result = mgr.getTool("tool_1", null);
            
            // 返回TracedTool，通过getTool()获取原始对象
            assertNotNull(result);
            assertSame(tool, result.getTool());
        }
        
        @Test
        @DisplayName("获取不存在的Tool返回null")
        void testGetNonexistentToolReturnsNull() {
            ToolMgr mgr = new ToolMgr();
            
            Object result = mgr.getTool("nonexistent", null);
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("成功移除Tool")
        void testRemoveToolSuccess() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool = new MockTool("tool_1");
            
            mgr.addTool("tool_1", tool);
            Object result = mgr.removeTool("tool_1");
            
            assertFalse(mgr.hasTool("tool_1"));
            assertSame(tool, result);
        }
        
        @Test
        @DisplayName("移除不存在的Tool返回null")
        void testRemoveNonexistentToolReturnsNull() {
            ToolMgr mgr = new ToolMgr();
            
            Object result = mgr.removeTool("nonexistent");
            
            assertNull(result);
        }
    }
    
    @Nested
    @DisplayName("MCP工具ID生成")
    class GenerateMcpToolIdTest {
        
        @Test
        @DisplayName("生成的MCP工具ID格式正确")
        void testGenerateMcpToolIdFormat() {
            String result = ToolMgr.generateMcpToolId("server_123", "my_server", "tool_a");
            
            assertEquals("server_123.my_server.tool_a", result);
        }
        
        @Test
        @DisplayName("包含特殊字符的ID生成")
        void testGenerateMcpToolIdWithSpecialChars() {
            String result = ToolMgr.generateMcpToolId("srv-1", "test_server", "get-data");
            
            assertEquals("srv-1.test_server.get-data", result);
        }
    }
    
    @Nested
    @DisplayName("MCP服务器操作")
    class McpServerOperationsTest {
        
        @Test
        @DisplayName("获取不存在的MCP服务器ID列表返回空")
        void testGetMcpServerIdsNonexistent() {
            ToolMgr mgr = new ToolMgr();
            
            List<String> result = mgr.getMcpServerIds("nonexistent");
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("获取不存在服务器的MCP工具返回null")
        void testGetMcpToolNonexistentServer() {
            ToolMgr mgr = new ToolMgr();
            
            Object result = mgr.getMcpTool("tool_a", "nonexistent", null);
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("获取不存在服务器的所有MCP工具返回null")
        void testGetMcpToolsNonexistentServer() {
            ToolMgr mgr = new ToolMgr();
            
            List<?> result = mgr.getMcpTools("nonexistent", null);
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除不存在的服务器（忽略）返回空列表")
        void testRemoveNonexistentServerIgnore() throws ExecutionException, InterruptedException {
            ToolMgr mgr = new ToolMgr();
            
            CompletableFuture<List<String>> result = mgr.removeToolServer("nonexistent", true);
            
            assertTrue(result.get().isEmpty());
        }
        
        @Test
        @DisplayName("移除不存在的服务器（不忽略）抛出异常")
        void testRemoveNonexistentServerNotIgnoreRaisesError() {
            ToolMgr mgr = new ToolMgr();
            
            CompletableFuture<List<String>> future = mgr.removeToolServer("nonexistent", false);
            
            ExecutionException exception = assertThrows(
                ExecutionException.class,
                future::get
            );
            
            assertTrue(exception.getCause() instanceof BaseError);
        }
    }
    
    @Nested
    @DisplayName("刷新MCP服务器")
    class RefreshToolServerTest {
        
        @Test
        @DisplayName("刷新不存在的服务器（跳过）返回空列表")
        void testRefreshNonexistentServerSkip() throws ExecutionException, InterruptedException {
            ToolMgr mgr = new ToolMgr();
            
            var result = mgr.refreshToolServer("nonexistent", true, false);
            
            assertTrue(result.get().isEmpty());
        }
        
        @Test
        @DisplayName("刷新不存在的服务器（抛出异常）")
        void testRefreshNonexistentServerRaises() {
            ToolMgr mgr = new ToolMgr();
            
            var future = mgr.refreshToolServer("nonexistent", false, false);
            
            ExecutionException exception = assertThrows(
                ExecutionException.class,
                future::get
            );
            
            assertTrue(exception.getCause() instanceof BaseError);
        }
    }
    
    @Nested
    @DisplayName("MCP服务器资源管理")
    class McpServerResourceTest {
        
        @Test
        @DisplayName("移除服务器清理关联工具")
        void testRemoveServerCleansTools() throws ExecutionException, InterruptedException {
            ToolMgr mgr = new ToolMgr();
            
            // 手动添加McpServerResource
            MockMcpServerConfig config = new MockMcpServerConfig("server_1", "test_server", "sse");
            List<String> toolIds = Arrays.asList("tool_1", "tool_2");
            
            // 添加关联的工具
            mgr.addTool("tool_1", new MockTool("tool_1"));
            mgr.addTool("tool_2", new MockTool("tool_2"));
            
            // 模拟添加MCP服务器资源
            mgr.addMcpServerResource("server_1", config.getServerName(), toolIds, 3600.0);
            
            // 移除服务器
            CompletableFuture<List<String>> result = mgr.removeToolServer("server_1", true);
            
            List<String> removedToolIds = result.get();
            assertEquals(2, removedToolIds.size());
            
            // 工具也应该被移除
            assertFalse(mgr.hasTool("tool_1"));
            assertFalse(mgr.hasTool("tool_2"));
        }
        
        @Test
        @DisplayName("未过期且非强制刷新不执行刷新")
        void testRefreshNotExpiredNoForce() throws ExecutionException, InterruptedException {
            ToolMgr mgr = new ToolMgr();
            
            // 添加MCP服务器资源（刚刚更新，1小时后过期）
            MockMcpServerConfig config = new MockMcpServerConfig("server_1", "test", "sse");
            mgr.addMcpServerResource("server_1", config.getServerName(), Arrays.asList(), 3600.0);
            
            var result = mgr.refreshToolServer("server_1", false, false);
            
            assertTrue(result.get().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("MCP工具操作")
    class McpToolOperationsTest {
        
        @Test
        @DisplayName("获取MCP工具")
        void testGetMcpToolSuccess() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool = new MockTool("server_1.test.tool_a");
            
            // 添加工具
            mgr.addTool("server_1.test.tool_a", tool);
            
            // 添加MCP服务器资源
            mgr.addMcpServerResource("server_1", "test", 
                Arrays.asList("server_1.test.tool_a"), 3600.0);
            
            TracerDecorator.TracedTool<MockTool> result = mgr.getMcpTool("tool_a", "server_1", null);
            
            assertNotNull(result);
            assertSame(tool, result.getTool());
        }
        
        @Test
        @DisplayName("获取服务器所有MCP工具")
        void testGetMcpToolsAll() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool1 = new MockTool("server_1.test.tool_a");
            MockTool tool2 = new MockTool("server_1.test.tool_b");
            
            // 添加工具
            mgr.addTool("server_1.test.tool_a", tool1);
            mgr.addTool("server_1.test.tool_b", tool2);
            
            // 添加MCP服务器资源
            mgr.addMcpServerResource("server_1", "test",
                Arrays.asList("server_1.test.tool_a", "server_1.test.tool_b"), 3600.0);
            
            List<?> result = mgr.getMcpTools("server_1", null);
            
            assertEquals(2, result.size());
        }
        
        @Test
        @DisplayName("通过服务器名称获取服务器ID列表")
        void testGetMcpServerIdsByName() {
            ToolMgr mgr = new ToolMgr();
            
            // 添加多个相同名称的服务器
            mgr.addMcpServerResource("server_1", "my_server", Arrays.asList(), 3600.0);
            mgr.addMcpServerResource("server_2", "my_server", Arrays.asList(), 3600.0);
            
            List<String> result = mgr.getMcpServerIds("my_server");
            
            assertEquals(2, result.size());
            assertTrue(result.contains("server_1"));
            assertTrue(result.contains("server_2"));
        }
    }
    
    @Nested
    @DisplayName("释放资源")
    class ReleaseTest {
        
        @Test
        @DisplayName("释放空管理器不抛出异常")
        void testReleaseEmptyManager() throws ExecutionException, InterruptedException {
            ToolMgr mgr = new ToolMgr();
            
            CompletableFuture<Void> result = mgr.release();
            
            // 不应该抛出异常
            assertDoesNotThrow(() -> result.get());
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("Tool完整生命周期")
        void testToolLifecycle() {
            ToolMgr mgr = new ToolMgr();
            MockTool tool = new MockTool("lifecycle_tool");
            
            // 添加
            mgr.addTool("tool_1", tool);
            assertTrue(mgr.hasTool("tool_1"));
            
            // 获取
            TracerDecorator.TracedTool<MockTool> tracedResult = mgr.getTool("tool_1", null);
            assertNotNull(tracedResult);
            assertSame(tool, tracedResult.getTool());
            
            // 移除
            Object removed = mgr.removeTool("tool_1");
            assertSame(tool, removed);
            assertFalse(mgr.hasTool("tool_1"));
            
            // 再次获取返回null
            tracedResult = mgr.getTool("tool_1", null);
            assertNull(tracedResult);
        }
    }
}

