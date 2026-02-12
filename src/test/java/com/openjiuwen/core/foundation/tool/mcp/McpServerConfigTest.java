package com.openjiuwen.core.foundation.tool.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP Server配置测试
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
class McpServerConfigTest {
    
    private McpServerConfig config;
    
    @BeforeEach
    void setUp() {
        config = new McpServerConfig();
    }
    
    /**
     * 测试默认值
     */
    @Test
    void testDefaults() {
        assertNotNull(config.getServerId());
        assertNotNull(config.getServerName());
        assertNotNull(config.getServerPath());
        assertEquals("sse", config.getClientType());
        assertTrue(config.getParams().isEmpty());
        assertTrue(config.getAuthHeaders().isEmpty());
        assertTrue(config.getAuthQueryParams().isEmpty());
    }
    
    /**
     * 测试设置配置值
     */
    @Test
    void testSetValues() {
        config.setServerName("test-server");
        config.setServerPath("/mcp");
        config.setClientType("stdio");
        Map<String, Object> params = Map.of("key", "value");
        config.setParams(params);
        
        assertEquals("test-server", config.getServerName());
        assertEquals("/mcp", config.getServerPath());
        assertEquals("stdio", config.getClientType());
        assertEquals(params, config.getParams());
    }
    
    /**
     * 测试构建器模式
     */
    @Test
    void testBuilder() {
        Map<String, Object> params = Map.of("param1", "value1");
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, String> queryParams = Map.of("api_key", "key123");
        
        McpServerConfig built = McpServerConfig.builder()
            .serverId("test-id")
            .serverName("test-server")
            .serverPath("/mcp")
            .clientType("sse")
            .params(params)
            .authHeaders(headers)
            .authQueryParams(queryParams)
            .build();
        
        assertEquals("test-id", built.getServerId());
        assertEquals("test-server", built.getServerName());
        assertEquals("/mcp", built.getServerPath());
        assertEquals("sse", built.getClientType());
        assertEquals(params, built.getParams());
        assertEquals(headers, built.getAuthHeaders());
        assertEquals(queryParams, built.getAuthQueryParams());
    }
}

