package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RESTful API Card测试
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
class RestfulApiCardTest {
    
    // 静态初始化块，在所有测试运行前设置环境变量
    static {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
    }
    
    // 静态初始化块，在所有测试运行前设置环境变量
    static {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
    }

    /**
     * 测试基本创建
     */
    @Test
    void testBasicCreation() {
        // 禁用SSRF防护以便测试（已在静态块中设置）
        
        RestfulApiCard card = new RestfulApiCard(
            "test_api",
            "Test API",
            "http://127.0.0.1:8000/api",
            "GET",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            30.0f,
            5 * 1024 * 1024,
            new HashMap<>()
        );
        
        assertEquals("test_api", card.getName());
        assertEquals("Test API", card.getDescription());
        assertEquals("http://127.0.0.1:8000/api", card.getUrl());
        assertEquals("GET", card.getMethod());
        assertEquals(30.0f, card.getTimeout(), 0.001f);
        assertEquals(5 * 1024 * 1024, card.getMaxResponseByteSize());
    }

    /**
     * 测试方法验证（大小写转换）
     */
    @Test
    void testMethodValidation() {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
        
        RestfulApiCard card = new RestfulApiCard(
            "test",
            "test",
            "http://127.0.0.1:8000",
            "get",  // 小写
            null, null, null,
            60.0f,
            10 * 1024 * 1024,
            null
        );
        
        // 应自动转为大写
        assertEquals("GET", card.getMethod());
    }

    /**
     * 测试不支持的HTTP方法
     */
    @Test
    void testUnsupportedMethod() {
        assertThrows(com.openjiuwen.core.common.exception.BaseError.class, () -> {
            new RestfulApiCard(
                "test",
                "test",
                "http://127.0.0.1:8000",
                "PUT",  // 不支持
                null, null, null,
                60.0f,
                10 * 1024 * 1024,
                null
            );
        });
    }

    /**
     * 测试URL验证（内网IP - 需要禁用SSRF防护）
     */
    @Test
    void testUrlValidation() {
        // 有效URL应该能创建（SSRF_PROTECT_ENABLED已在静态块中设置为false）
        assertDoesNotThrow(() -> {
            new RestfulApiCard(
                "test",
                "test",
                "http://127.0.0.1:8000",
                "GET",
                null, null, null,
                60.0f,
                10 * 1024 * 1024,
                null
            );
        });
    }

    /**
     * 测试ToolInfo生成
     */
    @Test
    void testToolInfo() {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
        
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("param1", Map.of("type", "string"));
        inputParams.put("properties", properties);
        
        RestfulApiCard card = new RestfulApiCard(
            "api_test",
            "API Test",
            "http://127.0.0.1:8000/test",
            "POST",
            null, null, null,
            60.0f,
            10 * 1024 * 1024,
            inputParams
        );
        
        ToolInfo toolInfo = card.toolInfo();
        
        assertEquals("function", toolInfo.type());
        assertEquals("api_test", toolInfo.name());
        assertEquals("API Test", toolInfo.description());
        assertNotNull(toolInfo.parameters());
    }

    /**
     * 测试默认值
     */
    @Test
    void testDefaultValues() {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
        
        RestfulApiCard card = new RestfulApiCard(
            "test",
            "test",
            "http://127.0.0.1:8000",
            null,  // 应使用默认POST
            null,  // 应创建空Map
            null,
            null,
            60.0f,
            10 * 1024 * 1024,
            null
        );
        
        assertEquals("POST", card.getMethod());
        assertNotNull(card.getHeaders());
        assertNotNull(card.getQueries());
        assertNotNull(card.getPaths());
        assertTrue(card.getHeaders().isEmpty());
    }

    /**
     * 测试Timeout范围验证
     */
    @Test
    void testTimeoutValidation() {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
        
        // 超出范围的timeout应使用默认值60秒
        RestfulApiCard card1 = new RestfulApiCard(
            "test", "test", "http://127.0.0.1:8000", "GET",
            null, null, null,
            0.5f,  // 小于1秒
            10 * 1024 * 1024,
            null
        );
        assertEquals(60.0f, card1.getTimeout(), 0.001f);
        
        RestfulApiCard card2 = new RestfulApiCard(
            "test", "test", "http://127.0.0.1:8000", "GET",
            null, null, null,
            400.0f,  // 大于300秒
            10 * 1024 * 1024,
            null
        );
        assertEquals(60.0f, card2.getTimeout(), 0.001f);
    }
}

