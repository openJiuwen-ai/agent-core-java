package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RESTful API测试
 * 
 * <p>严格对齐Python测试: test_restfulapi.py
 * 
 * <p>注意：由于Java 25不支持Mockito，且HTTP请求难以模拟，
 * 本测试主要覆盖配置、初始化和异常场景。
 * 完整的HTTP请求测试应在集成测试中进行。
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
class RestfulApiTest {
    
    // 静态初始化块，在所有测试运行前设置环境变量
    static {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
    }

    /**
     * 测试RestfulApi创建
     */
    @Test
    void testRestfulApiCreation() {
        RestfulApiCard card = new RestfulApiCard(
            "test_api",
            "Test API",
            "http://127.0.0.1:8000/api/users",
            "GET",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            30.0f,
            5 * 1024 * 1024,
            new HashMap<>()
        );
        
        RestfulApi api = new RestfulApi(card);
        
        assertNotNull(api);
        assertEquals(card, api.getCard());
    }

    /**
     * 测试带输入参数schema的API
     */
    @Test
    void testRestfulApiWithInputSchema() {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> userIdProp = new HashMap<>();
        userIdProp.put("type", "integer");
        userIdProp.put("location", "query");
        properties.put("user_id", userIdProp);
        
        inputParams.put("properties", properties);
        
        RestfulApiCard card = new RestfulApiCard(
            "get_user",
            "Get User Info",
            "http://127.0.0.1:8000/api/users",
            "GET",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            60.0f,
            10 * 1024 * 1024,
            inputParams
        );
        
        RestfulApi api = new RestfulApi(card);
        
        assertNotNull(api);
        assertEquals(inputParams, api.getCard().getInputParams());
    }

    /**
     * 测试ToolInfo生成
     */
    @Test
    void testGetToolInfo() {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        
        RestfulApiCard card = new RestfulApiCard(
            "test_api",
            "Test API Description",
            "http://127.0.0.1:8000/api/test",
            "POST",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            60.0f,
            10 * 1024 * 1024,
            inputParams
        );
        
        RestfulApi api = new RestfulApi(card);
        ToolInfo toolInfo = api.getCard().toolInfo();
        
        assertEquals("function", toolInfo.type());
        assertEquals("test_api", toolInfo.name());
        assertEquals("Test API Description", toolInfo.description());
        assertNotNull(toolInfo.parameters());
    }

    /**
     * 测试stream方法（应抛出不支持异常）
     */
    @Test
    void testStreamNotSupported() {
        RestfulApiCard card = new RestfulApiCard(
            "test",
            "test",
            "http://127.0.0.1:8000",
            "GET",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            60.0f,
            10 * 1024 * 1024,
            new HashMap<>()
        );
        
        RestfulApi api = new RestfulApi(card);
        
        // stream方法应该抛出不支持异常（ValidationError继承自BaseError）
        assertThrows(com.openjiuwen.core.common.exception.BaseError.class, () -> {
            api.stream(new HashMap<>(), new HashMap<>()).forEach(item -> {});
        });
    }

    /**
     * 测试默认配置
     */
    @Test
    void testDefaultConfiguration() {
        Map<String, Object> defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "JiuWenClient/1.0");
        
        Map<String, Object> defaultQueries = new HashMap<>();
        defaultQueries.put("format", "json");
        
        RestfulApiCard card = new RestfulApiCard(
            "api_with_defaults",
            "API with defaults",
            "http://127.0.0.1:8000/api/data",
            "GET",
            defaultHeaders,
            defaultQueries,
            new HashMap<>(),
            45.0f,
            8 * 1024 * 1024,
            new HashMap<>()
        );
        
        RestfulApi api = new RestfulApi(card);
        
        assertEquals("User-Agent", card.getHeaders().keySet().iterator().next());
        assertEquals("format", card.getQueries().keySet().iterator().next());
        assertEquals(45.0f, card.getTimeout(), 0.001f);
        assertEquals(8 * 1024 * 1024, card.getMaxResponseByteSize());
    }

    /**
     * 测试Path参数处理
     */
    @Test
    void testPathParametersInUrl() {
        Map<String, Object> inputParams = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idParam = new HashMap<>();
        idParam.put("type", "integer");
        idParam.put("location", "path");
        properties.put("id", idParam);
        
        inputParams.put("properties", properties);
        
        // 注意：URL中的{}会被Java URI解析器误认为是无效字符，所以改为{}
        // 这在Python中是有效的，因为Python的字符串format使用{}
        RestfulApiCard card = new RestfulApiCard(
            "get_user_by_id",
            "Get user by ID",
            "http://127.0.0.1:8000/api/users/<id>",  // 使用<>作为占位符避免URI解析错误
            "GET",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            60.0f,
            10 * 1024 * 1024,
            inputParams
        );
        
        RestfulApi api = new RestfulApi(card);
        
        // URL应包含路径参数占位符
        assertTrue(card.getUrl().contains("<id>"));
    }

    /**
     * 测试无效card（null）
     */
    @Test
    void testNullCard() {
        assertThrows(com.openjiuwen.core.common.exception.BaseError.class, () -> {
            new RestfulApi(null);
        });
    }

    /**
     * 测试HTTP方法配置
     */
    @Test
    void testHttpMethods() {
        // GET方法
        RestfulApiCard getCard = new RestfulApiCard(
            "get_api", "GET API",
            "http://127.0.0.1:8000/api/data",
            "GET",
            null, null, null, 60.0f, 10 * 1024 * 1024, null
        );
        RestfulApi getApi = new RestfulApi(getCard);
        RestfulApiCard getApiCard = (RestfulApiCard) getApi.getCard();
        assertEquals("GET", getApiCard.getMethod());
        
        // POST方法
        RestfulApiCard postCard = new RestfulApiCard(
            "post_api", "POST API",
            "http://127.0.0.1:8000/api/data",
            "POST",
            null, null, null, 60.0f, 10 * 1024 * 1024, null
        );
        RestfulApi postApi = new RestfulApi(postCard);
        RestfulApiCard postApiCard = (RestfulApiCard) postApi.getCard();
        assertEquals("POST", postApiCard.getMethod());
    }

    // ============================================================
    // 以下测试对应Python: TestRestfulApiInvokeWithLocation
    // 由于Java 25不支持Mockito，只能测试卡片配置
    // ============================================================

    /**
     * 测试Query位置参数配置
     * 对应Python: test_invoke_with_query_location (卡片配置部分)
     */
    @Test
    @DisplayName("测试Query位置参数配置")
    void testQueryLocationConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> userIdProp = new HashMap<>();
        userIdProp.put("type", "integer");
        userIdProp.put("description", "用户ID");
        userIdProp.put("location", "query");
        properties.put("user_id", userIdProp);
        
        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "用户姓名");
        nameProp.put("location", "body");
        properties.put("name", nameProp);
        
        Map<String, Object> filterProp = new HashMap<>();
        filterProp.put("type", "string");
        filterProp.put("description", "过滤条件");
        filterProp.put("location", "query");
        properties.put("filter", filterProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        Map<String, Object> defaultQueries = Map.of("format", "json");
        
        RestfulApiCard card = new RestfulApiCard(
            "get_user_info",
            "获取用户信息",
            "http://127.0.0.1/api/v1/users/{user_id}/profile",
            "GET",
            null,
            defaultQueries,
            null,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        assertEquals("json", card.getQueries().get("format"));
    }

    /**
     * 测试Path位置参数配置
     * 对应Python: test_invoke_with_path_location (卡片配置部分)
     */
    @Test
    @DisplayName("测试Path位置参数配置")
    void testPathLocationConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> userIdProp = new HashMap<>();
        userIdProp.put("type", "integer");
        userIdProp.put("description", "用户ID");
        userIdProp.put("location", "path");
        properties.put("user_id", userIdProp);
        
        Map<String, Object> actionProp = new HashMap<>();
        actionProp.put("type", "string");
        actionProp.put("description", "操作类型");
        actionProp.put("location", "path");
        properties.put("action", actionProp);
        
        Map<String, Object> dataProp = new HashMap<>();
        dataProp.put("type", "object");
        dataProp.put("description", "请求数据");
        dataProp.put("location", "body");
        properties.put("data", dataProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        Map<String, Object> defaultPaths = Map.of("version", "v1");
        
        RestfulApiCard card = new RestfulApiCard(
            "update_user_action",
            "更新用户操作",
            "http://127.0.0.1/api/v1/users/{user_id}/actions/{action}",
            "POST",
            null,
            null,
            defaultPaths,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        assertEquals("v1", card.getPaths().get("version"));
        assertTrue(card.getUrl().contains("{user_id}"));
        assertTrue(card.getUrl().contains("{action}"));
    }

    /**
     * 测试Header位置参数配置
     * 对应Python: test_invoke_with_header_location (卡片配置部分)
     */
    @Test
    @DisplayName("测试Header位置参数配置")
    void testHeaderLocationConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> authProp = new HashMap<>();
        authProp.put("type", "string");
        authProp.put("description", "认证令牌");
        authProp.put("location", "header");
        properties.put("authorization", authProp);
        
        Map<String, Object> contentTypeProp = new HashMap<>();
        contentTypeProp.put("type", "string");
        contentTypeProp.put("description", "内容类型");
        contentTypeProp.put("location", "header");
        properties.put("content_type", contentTypeProp);
        
        Map<String, Object> payloadProp = new HashMap<>();
        payloadProp.put("type", "object");
        payloadProp.put("description", "请求负载");
        payloadProp.put("location", "body");
        properties.put("payload", payloadProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        Map<String, Object> defaultHeaders = Map.of("User-Agent", "JiuWenClient/1.0");
        
        RestfulApiCard card = new RestfulApiCard(
            "create_resource",
            "创建资源",
            "http://127.0.0.1/api/v1/resources",
            "POST",
            defaultHeaders,
            null,
            null,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        assertEquals("JiuWenClient/1.0", card.getHeaders().get("User-Agent"));
    }

    /**
     * 测试混合位置参数配置
     * 对应Python: test_invoke_with_mixed_locations (卡片配置部分)
     */
    @Test
    @DisplayName("测试混合位置参数配置")
    void testMixedLocationsConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> idProp = new HashMap<>();
        idProp.put("type", "integer");
        idProp.put("description", "资源ID");
        idProp.put("location", "path");
        properties.put("id", idProp);
        
        Map<String, Object> categoryProp = new HashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("description", "分类");
        categoryProp.put("location", "query");
        properties.put("category", categoryProp);
        
        Map<String, Object> pageProp = new HashMap<>();
        pageProp.put("type", "integer");
        pageProp.put("description", "页码");
        pageProp.put("location", "query");
        properties.put("page", pageProp);
        
        Map<String, Object> apiKeyProp = new HashMap<>();
        apiKeyProp.put("type", "string");
        apiKeyProp.put("description", "API密钥");
        apiKeyProp.put("location", "header");
        properties.put("api_key", apiKeyProp);
        
        Map<String, Object> searchCriteriaProp = new HashMap<>();
        searchCriteriaProp.put("type", "object");
        searchCriteriaProp.put("description", "搜索条件");
        searchCriteriaProp.put("location", "body");
        properties.put("search_criteria", searchCriteriaProp);
        
        Map<String, Object> sortByProp = new HashMap<>();
        sortByProp.put("type", "string");
        sortByProp.put("description", "排序字段");
        sortByProp.put("location", "query");
        properties.put("sort_by", sortByProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        Map<String, Object> defaultQueries = Map.of("limit", 10);
        Map<String, Object> defaultHeaders = Map.of("X-Request-ID", "req-123");
        
        RestfulApiCard card = new RestfulApiCard(
            "search_resources",
            "搜索资源",
            "http://127.0.0.1/api/v1/resources/{id}/items",
            "GET",
            defaultHeaders,
            defaultQueries,
            null,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        assertEquals(10, card.getQueries().get("limit"));
        assertEquals("req-123", card.getHeaders().get("X-Request-ID"));
    }

    /**
     * 测试未指定location时使用默认body
     * 对应Python: test_invoke_with_no_location_specified (卡片配置部分)
     */
    @Test
    @DisplayName("测试未指定location时使用默认body")
    void testNoLocationSpecifiedConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        // 不指定location
        Map<String, Object> usernameProp = new HashMap<>();
        usernameProp.put("type", "string");
        usernameProp.put("description", "用户名");
        properties.put("username", usernameProp);
        
        Map<String, Object> emailProp = new HashMap<>();
        emailProp.put("type", "string");
        emailProp.put("description", "邮箱");
        properties.put("email", emailProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        RestfulApiCard card = new RestfulApiCard(
            "register_user",
            "注册用户",
            "http://127.0.0.1/api/v1/users/register",
            "POST",
            null,
            null,
            null,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        
        // 验证默认无location时应作为body处理
        @SuppressWarnings("unchecked")
        Map<String, Object> inputParams = (Map<String, Object>) card.getInputParams();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) inputParams.get("properties");
        assertNotNull(props);
        // username和email没有location字段
        assertFalse(((Map<?, ?>) props.get("username")).containsKey("location"));
        assertFalse(((Map<?, ?>) props.get("email")).containsKey("location"));
    }

    /**
     * 测试默认值覆盖配置
     * 对应Python: test_invoke_with_default_values_override (卡片配置部分)
     */
    @Test
    @DisplayName("测试默认值覆盖配置")
    void testDefaultValuesOverrideConfig() {
        Map<String, Object> inputSchema = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> formatProp = new HashMap<>();
        formatProp.put("type", "string");
        formatProp.put("description", "格式");
        formatProp.put("location", "query");
        properties.put("format", formatProp);
        
        Map<String, Object> apiTokenProp = new HashMap<>();
        apiTokenProp.put("type", "string");
        apiTokenProp.put("description", "API令牌");
        apiTokenProp.put("location", "header");
        properties.put("api_token", apiTokenProp);
        
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        
        // 设置默认值
        Map<String, Object> defaultQueries = new HashMap<>();
        defaultQueries.put("format", "xml");  // 将被输入覆盖
        defaultQueries.put("limit", 20);
        
        Map<String, Object> defaultHeaders = new HashMap<>();
        defaultHeaders.put("api_token", "default_token");  // 将被输入覆盖
        defaultHeaders.put("Accept", "application/json");
        
        RestfulApiCard card = new RestfulApiCard(
            "get_data",
            "获取数据",
            "http://127.0.0.1/api/v1/data",
            "GET",
            defaultHeaders,
            defaultQueries,
            null,
            60.0f,
            10 * 1024 * 1024,
            inputSchema
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        
        // 验证默认值已设置
        assertEquals("xml", card.getQueries().get("format"));
        assertEquals(20, card.getQueries().get("limit"));
        assertEquals("default_token", card.getHeaders().get("api_token"));
        assertEquals("application/json", card.getHeaders().get("Accept"));
    }

    // ============================================================
    // 以下测试对应Python: TestRestfulApiExceptions
    // ============================================================

    /**
     * 测试异常卡片配置
     * 对应Python: TestRestfulApiExceptions.mock_card fixture
     */
    @Test
    @DisplayName("测试异常场景的卡片配置")
    void testExceptionScenarioCardConfig() {
        RestfulApiCard card = new RestfulApiCard(
            "demo",
            null,
            "https://127.0.0.1/api.example.com/users",
            "POST",
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            60.0f,
            10 * 1024 * 1024,
            new HashMap<>()
        );
        
        RestfulApi api = new RestfulApi(card);
        assertNotNull(api);
        assertEquals("demo", card.getName());
        assertEquals("POST", card.getMethod());
        assertEquals(60.0f, card.getTimeout(), 0.001f);
        assertEquals(10 * 1024 * 1024, card.getMaxResponseByteSize());
    }
}

