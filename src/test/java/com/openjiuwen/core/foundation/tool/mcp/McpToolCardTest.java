package com.openjiuwen.core.foundation.tool.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP Tool Card测试
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
class McpToolCardTest {
    
    private McpToolCard card;
    
    @BeforeEach
    void setUp() {
        card = new McpToolCard(
            "test-name",
            "Test Description",
            new HashMap<>()
        );
    }
    
    /**
     * 测试基本创建
     */
    @Test
    void testBasicCreation() {
        assertEquals("test-name", card.getName());
        assertEquals("Test Description", card.getDescription());
        assertNotNull(card.getId());
    }
    
    /**
     * 测试设置server_name和server_id
     */
    @Test
    void testServerFields() {
        card.setServerName("mcp-server");
        card.setServerId("server-123");
        
        assertEquals("mcp-server", card.getServerName());
        assertEquals("server-123", card.getServerId());
    }
    
    /**
     * 测试继承自ToolCard的功能
     */
    @Test
    void testToolInfo() {
        assertNotNull(card.toolInfo());
        assertEquals("function", card.toolInfo().type());
        assertEquals("test-name", card.toolInfo().name());
        assertEquals("Test Description", card.toolInfo().description());
    }
    
    /**
     * 测试Builder模式
     */
    @Test
    void testBuilder() {
        McpToolCard built = McpToolCard.mcpBuilder()
            .name("builder-name")
            .description("Builder Description")
            .serverName("mcp-server")
            .serverId("server-id")
            .build();
        
        assertEquals("builder-name", built.getName());
        assertEquals("Builder Description", built.getDescription());
        assertEquals("mcp-server", built.getServerName());
        assertEquals("server-id", built.getServerId());
        assertNotNull(built.getId());
    }
}

